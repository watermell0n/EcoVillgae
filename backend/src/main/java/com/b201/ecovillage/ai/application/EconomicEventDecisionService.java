package com.b201.ecovillage.ai.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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
import com.b201.ecovillage.classroom.application.EconomicPolicyCommand;
import com.b201.ecovillage.classroom.application.EconomicPolicyCommandService;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

/**
 * API-AI-006 경제 이벤트 승인·거절을 처리한다.
 *
 * <p>승인은 초안 상태 전이와 경제 정책 반영을 한 트랜잭션에서 끝낸다. 7종 템플릿의 효과는
 * 모두 앞으로의 거래에만 적용되고 개인 잔액을 직접 바꾸지 않으므로 거래 원장에는 아무것도
 * 기록하지 않는다. 거절은 초안 상태만 바꾸고 도메인 변경을 만들지 않는다.
 *
 * <p>효과 수치는 AI가 아니라 교사가 edits로 확정하고, 허용 범위 검사는 ECONOMIC 정책의
 * 소유자인 {@link EconomicPolicyCommandService}가 그대로 담당한다.
 */
@Service
public class EconomicEventDecisionService {

    static final String ROUTE = "POST /classrooms/{classroomId}/ai/economic-events/{eventId}/decisions";
    private static final String APPROVE = "APPROVE";
    private static final String REJECT = "REJECT";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String ECONOMIC_POLICY_AREA = "ECONOMIC";

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final EconomicEventDraftRepository draftRepository;
    private final ClassroomPolicyVersionRepository policyRepository;
    private final EconomicPolicyCommandService economicPolicyCommandService;
    private final EconomicEventPolicyEffect policyEffect;
    private final ClassroomMutationSupport mutationSupport;
    private final AuditTrailService auditTrailService;
    private final Clock clock;

    public EconomicEventDecisionService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        EconomicEventDraftRepository draftRepository,
        ClassroomPolicyVersionRepository policyRepository,
        EconomicPolicyCommandService economicPolicyCommandService,
        EconomicEventPolicyEffect policyEffect,
        ClassroomMutationSupport mutationSupport,
        AuditTrailService auditTrailService,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.draftRepository = draftRepository;
        this.policyRepository = policyRepository;
        this.economicPolicyCommandService = economicPolicyCommandService;
        this.policyEffect = policyEffect;
        this.mutationSupport = mutationSupport;
        this.auditTrailService = auditTrailService;
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
            return finish(command, teacher, draft, requestHash, before, REJECTED, null);
        }

        TemplateType template = template(draft);
        if (template == TemplateType.INVESTMENT_NEWS) {
            // 투자 뉴스는 공개된 가격 단계에 연결해야 게시할 수 있다. 투자 도메인이 없으면
            // 연결 대상이 없으므로 승인을 거절한다. S15P11B201-289에서 풀린다.
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_INVESTMENT_STEP_BINDING",
                "INVESTMENT_NEWS needs the investment scenario domain, which is not implemented yet."
            );
        }
        Set<String> editableFields = policyEffect.editableFields(template, interestRateScope(draft));
        if (editableFields.isEmpty()) {
            requireNoEdits(command);
            draft.approve(now);
        } else {
            applyEconomicPolicy(command, template, editableFields);
            draft.applyEffect(now);
        }
        return finish(command, teacher, draft, requestHash, before, APPROVED, draft.getRevealedAt());
    }

    /**
     * 교사가 확정한 값만 기존 ECONOMIC 정책 위에 덮어써서 새 정책 버전을 만든다.
     * 템플릿이 허용하지 않은 항목이 오면 승인 자체를 거절한다.
     */
    private void applyEconomicPolicy(
        EconomicEventDecisionCommand command,
        TemplateType template,
        Set<String> editableFields
    ) {
        Map<String, Object> edits = command.edits() == null ? Map.of() : command.edits();
        if (edits.isEmpty()) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_ERROR",
                "Template " + template.name() + " requires edits for " + editableFields + "."
            );
        }
        for (String field : edits.keySet()) {
            if (!editableFields.contains(field)) {
                throw operation(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "FORBIDDEN_EVENT_EFFECT",
                    "Template " + template.name() + " cannot change " + field + "."
                );
            }
        }

        ClassroomPolicyVersion current = policyRepository
            .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
                command.classroomId(),
                ECONOMIC_POLICY_AREA
            )
            .orElse(null);
        Map<String, Object> values = new LinkedHashMap<>(
            current == null ? Map.of() : current.getPolicyValues()
        );
        values.putAll(edits);

        economicPolicyCommandService.update(new EconomicPolicyCommand(
            command.teacherSubject(),
            command.classroomId(),
            command.requestId(),
            command.correlationId(),
            command.expectedStateVersion(),
            scalar(values, EconomicEventPolicyEffect.PRICE_ADJUSTMENT_BPS),
            scalar(values, EconomicEventPolicyEffect.INFLATION_RATE_BPS),
            scalar(values, EconomicEventPolicyEffect.INCOME_TAX_RATE_BPS),
            scalar(values, "vat_rate_bps"),
            scalar(values, "trade_tax_rate_bps"),
            scalar(values, EconomicEventPolicyEffect.DEPOSIT_RATE_BPS),
            scalar(values, EconomicEventPolicyEffect.LOAN_RATE_BPS),
            tree(values, EconomicEventPolicyEffect.STOCK_POLICY),
            tree(values, EconomicEventPolicyEffect.REWARD_POLICY),
            tree(values, "limits"),
            current == null ? 0 : current.getVersion()
        ));
    }

    private EconomicEventDecisionResult finish(
        EconomicEventDecisionCommand command,
        UserAccount teacher,
        EconomicEventDraft draft,
        String requestHash,
        Map<String, Object> before,
        String status,
        Instant publishedAt
    ) {
        draftRepository.flush();
        EconomicEventDecisionResult result = new EconomicEventDecisionResult(
            draft.getId(),
            draft.getAiRunId(),
            status,
            draft.getStateVersion(),
            publishedAt
        );
        auditTrailService.record(
            command.classroomId(),
            teacher.getId(),
            "AI_ECONOMIC_EVENT_DECIDED",
            "ECONOMIC_EVENT_DRAFT",
            draft.getId(),
            command.requestId(),
            command.correlationId(),
            before,
            ClassroomMutationSupport.body(
                "status", draft.getStatus(),
                "decision", command.decision(),
                "reason", command.reason(),
                "edits", command.edits() == null ? Map.of() : command.edits()
            ),
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

    private void requireNoEdits(EconomicEventDecisionCommand command) {
        if (command.edits() != null && !command.edits().isEmpty()) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "FORBIDDEN_EVENT_EFFECT",
                "This template does not change the economic policy."
            );
        }
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

    private static int scalar(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw operation(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "VALIDATION_ERROR",
            key + " must be an integer."
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tree(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw operation(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "VALIDATION_ERROR",
            key + " must be an object."
        );
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
