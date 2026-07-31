package com.b201.ecovillage.quiz.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.audit.application.AuditTrailService;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.application.EffectiveAccessModeService;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStatus;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.quiz.domain.ActivityParticipant;
import com.b201.ecovillage.quiz.domain.ActivitySession;
import com.b201.ecovillage.quiz.repository.ActivityParticipantRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;
import com.b201.ecovillage.realtime.application.DisplaySessionService;

@Service
public class GuidedActivityCommandService {

    private static final String START_ROUTE =
        "/classrooms/{classroom_id}/guided-activities/{resource_id}/start-commands";
    private static final String FINISH_ROUTE =
        "/classrooms/{classroom_id}/guided-activities/{resource_id}/finish-commands";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);
    private static final Set<String> PUBLIC_FIELD_ALLOWLIST = Set.of(
        "scene",
        "prompt",
        "timer",
        "anonymous_counts",
        "public_result"
    );

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRepository studentRepository;
    private final ActivitySessionRepository activityRepository;
    private final ActivityParticipantRepository participantRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final RealtimeEventPublisher eventPublisher;
    private final EffectiveAccessModeService accessModeService;
    private final DisplaySessionService displaySessionService;
    private final AuditTrailService auditTrailService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public GuidedActivityCommandService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ClassroomRepository classroomRepository,
        ClassroomStudentRepository studentRepository,
        ActivitySessionRepository activityRepository,
        ActivityParticipantRepository participantRepository,
        IdempotencyRecordRepository idempotencyRepository,
        RealtimeEventPublisher eventPublisher,
        EffectiveAccessModeService accessModeService,
        DisplaySessionService displaySessionService,
        AuditTrailService auditTrailService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.classroomRepository = classroomRepository;
        this.studentRepository = studentRepository;
        this.activityRepository = activityRepository;
        this.participantRepository = participantRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.eventPublisher = eventPublisher;
        this.accessModeService = accessModeService;
        this.displaySessionService = displaySessionService;
        this.auditTrailService = auditTrailService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public GuidedActivityResult start(GuidedActivityStartCommand command) {
        validateStart(command);
        UserAccount teacher = authorize(command.teacherSubject(), command.classroomId());
        String requestHash = hashStart(command);
        GuidedActivityResult replay = replay(
            command.classroomId(),
            command.requestId(),
            teacher.getId(),
            START_ROUTE,
            requestHash
        );
        if (replay != null) {
            return replay;
        }
        requireActiveClassroom(command.classroomId());
        ActivitySession activity = activityRepository
            .findByClassroomIdAndId(command.classroomId(), command.activityId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The guided activity could not be found."
            ));
        String previousStatus = activity.getStatus();
        requireStateVersion(command.expectedStateVersion(), activity);
        if (activityRepository.existsByClassroomIdAndStatus(
            command.classroomId(),
            "LIVE"
        )) {
            throw operation(
                HttpStatus.CONFLICT,
                "ACTIVITY_ALREADY_LIVE",
                "Only one guided activity can be live in a classroom."
            );
        }
        List<ClassroomStudent> participants = studentRepository.findAllForUpdate(
            command.classroomId(),
            command.participantIds(),
            ClassroomStudentStatus.ACTIVE
        );
        if (participants.size() != command.participantIds().size()) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "PARTICIPANT_INVALID",
                "Every participant must be active in this classroom."
            );
        }
        Instant now = clock.instant();
        try {
            activity.start(now, Set.copyOf(command.publicFields()));
        } catch (IllegalStateException exception) {
            throw operation(
                HttpStatus.CONFLICT,
                "INVALID_STATE_TRANSITION",
                exception.getMessage()
            );
        }
        activityRepository.saveAndFlush(activity);
        participantRepository.saveAll(
            participants.stream()
                .map(student -> ActivityParticipant.joined(
                    uuidGenerator.generate(),
                    command.classroomId(),
                    command.activityId(),
                    student.getId(),
                    now
                ))
                .toList()
        );
        GuidedActivityResult result = new GuidedActivityResult(
            activity.getId(),
            activity.getStatus(),
            activity.getStateVersion()
        );
        eventPublisher.publish(
            command.classroomId(),
            "ACTIVITY",
            command.activityId(),
            "activity.started",
            "ACTIVITY",
            command.activityId(),
            activity.getStateVersion(),
            Map.of(
                "activity_id", activity.getId().toString(),
                "activity_type", activity.getActivityType(),
                "starts_at", activity.getStartsAt().toString(),
                "phase", "LIVE",
                "screen_id", command.correlationId().toString()
            ),
            true
        );
        eventPublisher.publish(
            command.classroomId(),
            "ACTIVITY",
            command.activityId(),
            "activity.participation.updated",
            "ACTIVITY",
            command.activityId(),
            activity.getStateVersion(),
            Map.of(
                "activity_id", activity.getId().toString(),
                "joined_count", participants.size(),
                "submitted_count", 0,
                "total_count", participants.size()
            ),
            true
        );
        auditTrailService.record(
            command.classroomId(),
            teacher.getId(),
            "GUIDED_ACTIVITY_STARTED",
            "ACTIVITY",
            command.activityId(),
            command.requestId(),
            command.correlationId(),
            Map.of("status", previousStatus),
            Map.of(
                "status", activity.getStatus(),
                "participant_count", participants.size(),
                "public_fields", command.publicFields()
            ),
            "ACTIVITY",
            "지도 활동이 시작되었습니다."
        );
        remember(
            command.classroomId(),
            command.requestId(),
            teacher.getId(),
            START_ROUTE,
            requestHash,
            result,
            now
        );
        return result;
    }

    @Transactional
    public GuidedActivityResult finish(GuidedActivityFinishCommand command) {
        validateFinish(command);
        UserAccount teacher = authorize(command.teacherSubject(), command.classroomId());
        String requestHash = hashFinish(command);
        GuidedActivityResult replay = replay(
            command.classroomId(),
            command.requestId(),
            teacher.getId(),
            FINISH_ROUTE,
            requestHash
        );
        if (replay != null) {
            return replay;
        }
        requireActiveClassroom(command.classroomId());
        ActivitySession activity = activityRepository
            .findByClassroomIdAndId(command.classroomId(), command.activityId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The guided activity could not be found."
            ));
        String previousStatus = activity.getStatus();
        requireStateVersion(command.expectedStateVersion(), activity);
        Instant now = clock.instant();
        try {
            if (command.command() == GuidedActivityFinishType.END) {
                activity.beginFinalizing(now);
            } else {
                activity.cancel(now);
            }
        } catch (IllegalStateException exception) {
            throw operation(
                HttpStatus.CONFLICT,
                "INVALID_STATE_TRANSITION",
                exception.getMessage()
            );
        }
        List<ActivityParticipant> participants = participantRepository
            .findAllByClassroomIdAndActivitySessionId(
                command.classroomId(),
                command.activityId()
            );
        participants.forEach(participant -> {
            if (command.command() == GuidedActivityFinishType.END) {
                participant.complete(now);
            } else {
                participant.cancel(now);
            }
        });
        participantRepository.saveAll(participants);
        activityRepository.saveAndFlush(activity);
        GuidedActivityResult result = new GuidedActivityResult(
            activity.getId(),
            activity.getStatus(),
            activity.getStateVersion()
        );
        String nextMode = accessModeService.get(command.classroomId()).mode().name();
        String eventType = command.command() == GuidedActivityFinishType.END
            ? "activity.finished"
            : "activity.canceled";
        Map<String, Object> payload = command.command() == GuidedActivityFinishType.END
            ? Map.of(
                "result_status", activity.getStatus(),
                "next_app_mode", nextMode
            )
            : Map.of(
                "reason_code",
                command.reason() == null || command.reason().isBlank()
                    ? "TEACHER_CANCELED"
                    : command.reason(),
                "next_app_mode", nextMode
            );
        eventPublisher.publish(
            command.classroomId(),
            "ACTIVITY",
            command.activityId(),
            eventType,
            "ACTIVITY",
            command.activityId(),
            activity.getStateVersion(),
            payload,
            true
        );
        displaySessionService.revokeAllForActivity(command.activityId());
        auditTrailService.record(
            command.classroomId(),
            teacher.getId(),
            command.command() == GuidedActivityFinishType.END
                ? "GUIDED_ACTIVITY_FINISHED"
                : "GUIDED_ACTIVITY_CANCELED",
            "ACTIVITY",
            command.activityId(),
            command.requestId(),
            command.correlationId(),
            Map.of("status", previousStatus),
            Map.of(
                "status", activity.getStatus(),
                "next_app_mode", nextMode,
                "reason", command.reason() == null ? "" : command.reason()
            ),
            "ACTIVITY",
            command.command() == GuidedActivityFinishType.END
                ? "지도 활동 종료 처리가 시작되었습니다."
                : "지도 활동이 취소되었습니다."
        );
        remember(
            command.classroomId(),
            command.requestId(),
            teacher.getId(),
            FINISH_ROUTE,
            requestHash,
            result,
            now
        );
        return result;
    }

    private UserAccount authorize(String subject, UUID classroomId) {
        authorizationService.authorize(
            subject,
            classroomId,
            ClassroomPermission.MANAGE_GUIDED_ACTIVITIES,
            null
        );
        return userRepository.findByExternalSubject(subject)
            .orElseThrow(() -> operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A valid teacher session is required."
            ));
    }

    private void requireActiveClassroom(UUID classroomId) {
        Classroom classroom = classroomRepository.findById(classroomId)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The classroom could not be found."
            ));
        if (classroom.getStatus() != ClassroomStatus.ACTIVE) {
            throw operation(
                HttpStatus.CONFLICT,
                "CLASS_NOT_ACTIVE",
                "A guided activity can run only in an active classroom."
            );
        }
    }

    private static void requireStateVersion(
        Long expectedStateVersion,
        ActivitySession activity
    ) {
        if (
            expectedStateVersion != null
                && expectedStateVersion != activity.getStateVersion()
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The guided activity state has changed."
            );
        }
    }

    private GuidedActivityResult replay(
        UUID classroomId,
        UUID requestId,
        UUID actorId,
        String route,
        String requestHash
    ) {
        IdempotencyRecord record = idempotencyRepository
            .findForReplay(classroomId, actorId, route, requestId.toString())
            .orElse(null);
        if (record == null) {
            return null;
        }
        if (!record.hasRequestHash(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        Map<String, Object> body = record.getResponseBody();
        return new GuidedActivityResult(
            UUID.fromString(body.get("activity_id").toString()),
            body.get("status").toString(),
            ((Number) body.get("state_version")).longValue()
        );
    }

    private void remember(
        UUID classroomId,
        UUID requestId,
        UUID actorId,
        String route,
        String requestHash,
        GuidedActivityResult result,
        Instant now
    ) {
        idempotencyRepository.save(
            IdempotencyRecord.completed(
                uuidGenerator.generate(),
                classroomId,
                actorId,
                route,
                requestId.toString(),
                requestHash,
                HttpStatus.OK.value(),
                Map.of(
                    "activity_id", result.activityId(),
                    "status", result.status(),
                    "state_version", result.stateVersion()
                ),
                null,
                now.plus(IDEMPOTENCY_TTL),
                now
            )
        );
    }

    private static void validateStart(GuidedActivityStartCommand command) {
        if (
            command == null
                || command.classroomId() == null
                || command.activityId() == null
                || command.requestId() == null
                || command.participantIds() == null
                || command.participantIds().isEmpty()
                || command.publicFields() == null
                || new HashSet<>(command.participantIds()).size()
                    != command.participantIds().size()
                || new HashSet<>(command.publicFields()).size()
                    != command.publicFields().size()
        ) {
            throw validation("Check the guided activity start values.");
        }
        if (!PUBLIC_FIELD_ALLOWLIST.containsAll(command.publicFields())) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "PUBLIC_DATA_ONLY",
                "publicFields contains a field that may expose personal data."
            );
        }
    }

    private static void validateFinish(GuidedActivityFinishCommand command) {
        if (
            command == null
                || command.classroomId() == null
                || command.activityId() == null
                || command.requestId() == null
                || command.command() == null
                || command.reason() != null && command.reason().length() > 500
        ) {
            throw validation("Check the guided activity finish values.");
        }
    }

    private static String hashStart(GuidedActivityStartCommand command) {
        return sha256(
            command.classroomId() + "|" + command.activityId() + "|"
                + command.participantIds() + "|" + command.publicFields() + "|"
                + command.expectedStateVersion()
        );
    }

    private static String hashFinish(GuidedActivityFinishCommand command) {
        return sha256(
            command.classroomId() + "|" + command.activityId() + "|"
                + command.command() + "|" + command.reason() + "|"
                + command.expectedStateVersion()
        );
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash the activity command.", exception);
        }
    }

    private static ClassroomOperationException validation(String message) {
        return operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
