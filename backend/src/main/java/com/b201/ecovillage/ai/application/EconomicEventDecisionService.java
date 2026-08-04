package com.b201.ecovillage.ai.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.ai.contract.EconomicEventContract.InterestRateScope;
import com.b201.ecovillage.ai.contract.EconomicEventContract.TemplateType;
import com.b201.ecovillage.ai.domain.EconomicEventDraft;
import com.b201.ecovillage.ai.repository.EconomicEventDraftRepository;
import com.b201.ecovillage.audit.application.AuditTrailService;
import com.b201.ecovillage.classroom.application.ClassroomMutationSupport;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;

/**
 * API-AI-006 경제 이벤트 승인·거절을 처리한다.
 *
 * <p>승인은 초안 상태 전이와 템플릿별 효과 적용을 한 트랜잭션에서 끝낸다. 효과 적용은
 * {@link EconomicEventEffectDispatcher}가 도메인 진입점으로 보내며, 이 서비스는 정책 버전이나
 * 원장 거래를 직접 만들지 않는다. 일부만 반영된 상태는 생기지 않는다.
 *
 * <p>실패하면 모든 변경을 되돌리고 초안은 DRAFT로 남겨 재시도할 수 있게 한다. 초안을 FAILED로
 * 고정하지 않고 실패 사유와 시도 이력만 별도 트랜잭션에 남긴다.
 *
 * <p>거절은 초안 상태만 바꾸고 도메인 변경을 만들지 않는다.
 */
@Service
public class EconomicEventDecisionService {

    static final String ROUTE = "POST /classrooms/{classroomId}/ai/economic-events/{eventId}/decisions";
    private static final String APPROVE = "APPROVE";
    private static final String REJECT = "REJECT";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    /** WS-ECO-001 {@code classrooms/{C}/economy} 채널의 스트림이다. */
    private static final String ECONOMY_STREAM = "ECONOMY";
    private static final String PUBLISHED_EVENT_TYPE = "economic_event.published";
    private static final String EVENT_AGGREGATE_TYPE = "ECONOMIC_EVENT_DRAFT";

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final EconomicEventDraftRepository draftRepository;
    private final EconomicEventEffectDispatcher effectDispatcher;
    private final EconomicEventAttemptRecorder attemptRecorder;
    private final ClassroomMutationSupport mutationSupport;
    private final AuditTrailService auditTrailService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final Clock clock;

    public EconomicEventDecisionService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        EconomicEventDraftRepository draftRepository,
        EconomicEventEffectDispatcher effectDispatcher,
        EconomicEventAttemptRecorder attemptRecorder,
        ClassroomMutationSupport mutationSupport,
        AuditTrailService auditTrailService,
        RealtimeEventPublisher realtimeEventPublisher,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.draftRepository = draftRepository;
        this.effectDispatcher = effectDispatcher;
        this.attemptRecorder = attemptRecorder;
        this.mutationSupport = mutationSupport;
        this.auditTrailService = auditTrailService;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.clock = clock;
    }

    @Transactional
    public EconomicEventDecisionResult decide(EconomicEventDecisionCommand command) {
        validate(command);
        authorizationService.authorize(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.DECIDE_AI_EVENT_DRAFT,
            null
        );
        UserAccount teacher = userRepository.findByExternalSubject(command.teacherSubject())
            .orElseThrow(() -> operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A teacher session is required."
            ));

        String requestHash = mutationSupport.hash(ClassroomMutationSupport.body(
            "classroom_id", command.classroomId(),
            "event_id", command.eventId(),
            "decision", command.decision(),
            "edits", command.edits(),
            "reason", command.reason(),
            "expected_event_version", command.expectedEventVersion(),
            "expected_state_version", command.expectedStateVersion()
        ));
        Map<String, Object> replay = mutationSupport.replay(
            command.classroomId(),
            teacher.getId(),
            ROUTE,
            command.requestId(),
            requestHash
        );
        if (replay != null) {
            return replayed(replay);
        }

        EconomicEventDraft draft = draftRepository
            .findByClassroomIdAndId(command.classroomId(), command.eventId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The economic event draft was not found."
            ));
        if (!draft.isUnderReview()) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The economic event draft is no longer under review."
            );
        }
        if (command.expectedEventVersion() != draft.getStateVersion()) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The economic event draft changed. Reload and try again."
            );
        }

        Instant now = clock.instant();
        Map<String, Object> before = ClassroomMutationSupport.body(
            "status", draft.getStatus(),
            "state_version", draft.getStateVersion()
        );

        if (REJECT.equals(command.decision())) {
            draft.reject(now);
            return finish(command, teacher, draft, requestHash, before, REJECTED, null, Map.of());
        }

        TemplateType template = template(draft);
        Map<String, Object> applied = applyEffect(command, teacher, template, draft);
        draft.applyEffect(now);
        return finish(
            command,
            teacher,
            draft,
            requestHash,
            before,
            APPROVED,
            draft.getRevealedAt(),
            applied
        );
    }

    /**
     * 템플릿별 도메인 진입점을 호출한다. 실패하면 이 트랜잭션 전체가 롤백되고 초안은 DRAFT로
     * 남는다. 실패 사유와 시도 이력만 별도 트랜잭션에서 커밋해 재시도할 수 있게 한다.
     */
    private Map<String, Object> applyEffect(
        EconomicEventDecisionCommand command,
        UserAccount teacher,
        TemplateType template,
        EconomicEventDraft draft
    ) {
        try {
            return effectDispatcher.apply(
                command,
                template,
                interestRateScope(draft),
                teacher.getId(),
                draft.getInvestmentPriceStepId()
            );
        } catch (RuntimeException failure) {
            attemptRecorder.recordFailure(
                command.classroomId(),
                teacher.getId(),
                command.eventId(),
                command.requestId(),
                command.correlationId(),
                template.name(),
                failureCode(failure),
                failure.getMessage(),
                command.edits()
            );
            throw failure;
        }
    }

    private EconomicEventDecisionResult finish(
        EconomicEventDecisionCommand command,
        UserAccount teacher,
        EconomicEventDraft draft,
        String requestHash,
        Map<String, Object> before,
        String status,
        Instant publishedAt,
        Map<String, Object> applied
    ) {
        draftRepository.flush();
        EconomicEventDecisionResult result = new EconomicEventDecisionResult(
            draft.getId(),
            draft.getAiRunId(),
            status,
            draft.getStateVersion(),
            publishedAt
        );
        if (APPROVED.equals(status)) {
            publishToStudents(draft, publishedAt);
        }

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", draft.getStatus());
        after.put("decision", command.decision());
        after.put("reason", command.reason());
        // 원장 거래의 집행 주체는 SYSTEM이므로 승인한 교사는 감사 기록으로만 추적한다.
        after.put("approved_by", teacher.getId().toString());
        after.put("edits", command.edits() == null ? Map.of() : Map.copyOf(command.edits()));
        after.put("applied_effect", applied);
        auditTrailService.record(
            command.classroomId(),
            teacher.getId(),
            "AI_ECONOMIC_EVENT_DECIDED",
            "ECONOMIC_EVENT_DRAFT",
            draft.getId(),
            command.requestId(),
            command.correlationId(),
            before,
            after,
            "AI",
            APPROVED.equals(status)
                ? "교사가 경제 이벤트를 승인해 학생에게 게시했습니다."
                : "교사가 경제 이벤트를 거절했습니다."
        );

        // IdempotencyRecord는 응답 본문을 Map.copyOf로 다시 담으므로 null 값을 넣을 수 없다.
        Map<String, Object> remembered = new LinkedHashMap<>();
        remembered.put("event_id", result.eventId().toString());
        remembered.put("status", result.status());
        remembered.put("state_version", result.stateVersion());
        if (result.aiRunId() != null) {
            remembered.put("ai_run_id", result.aiRunId().toString());
        }
        if (result.publishedAt() != null) {
            remembered.put("published_at", result.publishedAt().toString());
        }
        mutationSupport.remember(
            command.classroomId(),
            teacher.getId(),
            ROUTE,
            command.requestId(),
            requestHash,
            remembered
        );
        return result;
    }

    /**
     * EVT-ACT-007 economic_event.published를 WS-ECO-001 스트림에 올린다.
     *
     * <p>아웃박스 행이 이 트랜잭션과 함께 커밋되므로 승인이 롤백되면 발행 대상도 남지 않는다.
     * 실제 소켓 전달은 아웃박스를 비우는 쪽이 담당한다.
     *
     * <p>학생도 받는 채널이라 공개 안전성 검사를 켠다. 개인 식별자와 잔액은 payload에 넣지 않는다.
     */
    private void publishToStudents(EconomicEventDraft draft, Instant publishedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", draft.getId().toString());
        payload.put("template_type", String.valueOf(draft.getEconomicEffect().get("template_type")));
        payload.put("title", draft.getTitle());
        payload.put("description", draft.getContent());
        payload.put("effective_from", publishedAt == null ? null : publishedAt.toString());
        realtimeEventPublisher.publish(
            draft.getClassroomId(),
            ECONOMY_STREAM,
            null,
            PUBLISHED_EVENT_TYPE,
            EVENT_AGGREGATE_TYPE,
            draft.getId(),
            draft.getStateVersion(),
            payload,
            true
        );
    }

    private static EconomicEventDecisionResult replayed(Map<String, Object> replay) {
        Object aiRunId = replay.get("ai_run_id");
        Object publishedAt = replay.get("published_at");
        return new EconomicEventDecisionResult(
            UUID.fromString(replay.get("event_id").toString()),
            aiRunId == null ? null : UUID.fromString(aiRunId.toString()),
            replay.get("status").toString(),
            ((Number) replay.get("state_version")).longValue(),
            publishedAt == null ? null : Instant.parse(publishedAt.toString())
        );
    }

    private static String failureCode(RuntimeException failure) {
        if (failure instanceof ClassroomOperationException operationFailure) {
            return operationFailure.getCode();
        }
        return failure.getClass().getSimpleName();
    }

    private TemplateType template(EconomicEventDraft draft) {
        Object value = draft.getEconomicEffect().get("template_type");
        try {
            return TemplateType.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "FORBIDDEN_EVENT_EFFECT",
                "The stored economic event template is not supported."
            );
        }
    }

    private static InterestRateScope interestRateScope(EconomicEventDraft draft) {
        Object value = draft.getEconomicEffect().get("interest_rate_scope");
        if (value == null) {
            return null;
        }
        try {
            return InterestRateScope.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void validate(EconomicEventDecisionCommand command) {
        if (command.teacherSubject() == null || command.teacherSubject().isBlank()) {
            throw operation(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "A teacher session is required.");
        }
        if (!APPROVE.equals(command.decision()) && !REJECT.equals(command.decision())) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "decision must be APPROVE or REJECT."
            );
        }
        if (command.reason() == null || command.reason().isBlank()) {
            throw operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "reason must not be blank.");
        }
        if (command.expectedEventVersion() == null) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "expected_event_version must be supplied."
            );
        }
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
