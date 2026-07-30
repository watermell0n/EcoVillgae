package com.b201.ecovillage.classroom.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.audit.application.AuditTrailService;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStatus;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;

@Service
public class AccessModeCommandService {

    private static final String ROUTE =
        "/classrooms/{classroom_id}/access-mode-commands";
    private static final String ACCESS_POLICY = "ACCESS_MODE";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);
    private static final int DEFAULT_DURATION_MINUTES = 30;
    private static final int MAX_DURATION_MINUTES = 480;

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomPolicyVersionRepository policyRepository;
    private final ClassroomModuleRepository moduleRepository;
    private final ActivitySessionRepository activityRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final RealtimeEventPublisher eventPublisher;
    private final AuditTrailService auditTrailService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public AccessModeCommandService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ClassroomRepository classroomRepository,
        ClassroomPolicyVersionRepository policyRepository,
        ClassroomModuleRepository moduleRepository,
        ActivitySessionRepository activityRepository,
        IdempotencyRecordRepository idempotencyRepository,
        RealtimeEventPublisher eventPublisher,
        AuditTrailService auditTrailService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.classroomRepository = classroomRepository;
        this.policyRepository = policyRepository;
        this.moduleRepository = moduleRepository;
        this.activityRepository = activityRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.eventPublisher = eventPublisher;
        this.auditTrailService = auditTrailService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public AccessModeResult execute(AccessModeCommand command) {
        validate(command);
        authorizationService.authorize(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.CONTROL_CLASSROOM_ACCESS,
            null
        );
        UserAccount teacher = userRepository
            .findByExternalSubject(command.teacherSubject())
            .orElseThrow(() -> operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A valid teacher session is required."
            ));
        String requestHash = hash(command);
        AccessModeResult replay = replay(command, teacher.getId(), requestHash);
        if (replay != null) {
            return replay;
        }

        Classroom classroom = classroomRepository.findById(command.classroomId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The classroom could not be found."
            ));
        if (classroom.getStatus() != ClassroomStatus.ACTIVE) {
            throw operation(
                HttpStatus.CONFLICT,
                "CLASS_NOT_ACTIVE",
                "Access mode can change only in an active classroom."
            );
        }
        if (
            command.expectedStateVersion() != null
                && command.expectedStateVersion() != classroom.getStateVersion()
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The classroom state has changed."
            );
        }
        if (activityRepository.existsByClassroomIdAndStatus(
            command.classroomId(),
            "LIVE"
        )) {
            throw operation(
                HttpStatus.CONFLICT,
                "GUIDED_MODE_ACTIVE",
                "End the guided activity before changing autonomous access."
            );
        }

        Instant now = clock.instant();
        ClassroomPolicyVersion previous = policyRepository
            .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
                command.classroomId(),
                ACCESS_POLICY
            )
            .orElse(null);
        int nextVersion = previous == null ? 1 : previous.getVersion() + 1;
        Instant expiresAt = calculateExpiry(command, previous, now);
        AppAccessMode mode = command.command() == AccessModeCommandType.CLOSE
            ? AppAccessMode.CLOSED
            : AppAccessMode.AUTONOMOUS;
        if (previous != null) {
            previous.supersede(now);
        }
        ClassroomPolicyVersion policy = ClassroomPolicyVersion.accessMode(
            uuidGenerator.generate(),
            command.classroomId(),
            nextVersion,
            Map.of(
                "command", command.command().name(),
                "mode", mode.name(),
                "duration_minutes",
                command.durationMinutes() == null
                    ? DEFAULT_DURATION_MINUTES
                    : command.durationMinutes(),
                "expires_at",
                expiresAt == null ? "" : expiresAt.toString()
            ),
            now
        );
        policyRepository.save(policy);
        classroom.setAppModeOverride(mode.name(), now);
        classroomRepository.saveAndFlush(classroom);

        List<String> moduleCodes = mode == AppAccessMode.AUTONOMOUS
            ? moduleRepository
                .findAllByClassroomIdAndStatusOrderByModuleCode(
                    command.classroomId(),
                    "ACTIVE"
                )
                .stream()
                .map(module -> module.getModuleCode())
                .toList()
            : List.of();
        AccessModeResult result = new AccessModeResult(
            mode,
            "MANUAL",
            expiresAt,
            moduleCodes,
            classroom.getStateVersion()
        );
        eventPublisher.publish(
            command.classroomId(),
            "CLASSROOM_LIFECYCLE",
            null,
            "classroom.app_mode.changed",
            "CLASSROOM",
            command.classroomId(),
            classroom.getStateVersion(),
            Map.of(
                "mode", result.mode().name(),
                "source", result.source(),
                "starts_at", now.toString(),
                "ends_at",
                result.expiresAt() == null
                    ? now.toString()
                    : result.expiresAt().toString()
            ),
            true
        );
        Map<String, Object> beforeSummary = previous == null
            ? Map.of("mode", "UNSET")
            : Map.of(
                "mode", previous.getPolicyValues().getOrDefault("mode", "UNSET"),
                "policy_version", previous.getVersion()
            );
        auditTrailService.record(
            command.classroomId(),
            teacher.getId(),
            "CLASSROOM_ACCESS_MODE_CHANGED",
            "CLASSROOM",
            command.classroomId(),
            command.requestId(),
            command.correlationId(),
            beforeSummary,
            Map.of(
                "mode", result.mode().name(),
                "source", result.source(),
                "policy_version", nextVersion,
                "expires_at",
                result.expiresAt() == null ? "" : result.expiresAt().toString()
            ),
            "POLICY",
            "교실 이용 모드가 변경되었습니다."
        );
        remember(command, teacher.getId(), requestHash, result, now);
        return result;
    }

    private Instant calculateExpiry(
        AccessModeCommand command,
        ClassroomPolicyVersion previous,
        Instant now
    ) {
        if (command.command() == AccessModeCommandType.CLOSE) {
            return null;
        }
        int duration = command.durationMinutes() == null
            ? DEFAULT_DURATION_MINUTES
            : command.durationMinutes();
        if (duration < 1 || duration > MAX_DURATION_MINUTES) {
            throw validation("durationMinutes must be between 1 and 480.");
        }
        if (command.command() == AccessModeCommandType.EXTEND) {
            Instant previousExpiry = previous == null
                ? null
                : EffectiveAccessModeService.expiresAt(previous.getPolicyValues());
            if (
                previousExpiry == null
                    || !previousExpiry.isAfter(now)
                    || !"AUTONOMOUS".equals(previous.getPolicyValues().get("mode"))
            ) {
                throw operation(
                    HttpStatus.CONFLICT,
                    "AUTONOMOUS_MODE_NOT_ACTIVE",
                    "Only an active manual autonomous period can be extended."
                );
            }
            return previousExpiry.plus(Duration.ofMinutes(duration));
        }
        return now.plus(Duration.ofMinutes(duration));
    }

    private AccessModeResult replay(
        AccessModeCommand command,
        UUID actorId,
        String requestHash
    ) {
        IdempotencyRecord record = idempotencyRepository
            .findForReplay(
                command.classroomId(),
                actorId,
                ROUTE,
                command.requestId().toString()
            )
            .orElse(null);
        if (record == null) {
            return null;
        }
        if (!record.hasRequestHash(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        Map<String, Object> body = record.getResponseBody();
        @SuppressWarnings("unchecked")
        List<String> modules = ((List<Object>) body.getOrDefault(
            "module_codes",
            List.of()
        )).stream().map(Object::toString).toList();
        Object expiresAt = body.get("expires_at");
        return new AccessModeResult(
            AppAccessMode.valueOf(body.get("mode").toString()),
            body.get("source").toString(),
            expiresAt == null || expiresAt.toString().isBlank()
                ? null
                : Instant.parse(expiresAt.toString()),
            modules,
            ((Number) body.get("state_version")).longValue()
        );
    }

    private void remember(
        AccessModeCommand command,
        UUID actorId,
        String requestHash,
        AccessModeResult result,
        Instant now
    ) {
        idempotencyRepository.save(
            IdempotencyRecord.completed(
                uuidGenerator.generate(),
                command.classroomId(),
                actorId,
                ROUTE,
                command.requestId().toString(),
                requestHash,
                HttpStatus.OK.value(),
                Map.of(
                    "mode", result.mode().name(),
                    "source", result.source(),
                    "expires_at",
                    result.expiresAt() == null ? "" : result.expiresAt().toString(),
                    "module_codes", result.moduleCodes(),
                    "state_version", result.stateVersion()
                ),
                null,
                now.plus(IDEMPOTENCY_TTL),
                now
            )
        );
    }

    private static void validate(AccessModeCommand command) {
        if (
            command == null
                || command.classroomId() == null
                || command.requestId() == null
                || command.command() == null
        ) {
            throw validation("Check the access mode command values.");
        }
        if (
            command.command() == AccessModeCommandType.CLOSE
                && command.durationMinutes() != null
        ) {
            throw validation("CLOSE does not accept durationMinutes.");
        }
    }

    private static String hash(AccessModeCommand command) {
        return sha256(
            command.classroomId() + "|" + command.command() + "|"
                + command.durationMinutes() + "|" + command.expectedStateVersion()
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
            throw new IllegalStateException("Could not hash the access command.", exception);
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
