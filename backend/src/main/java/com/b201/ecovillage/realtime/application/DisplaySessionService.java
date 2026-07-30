package com.b201.ecovillage.realtime.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.audit.application.AuditTrailService;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.quiz.domain.ActivitySession;
import com.b201.ecovillage.quiz.repository.ActivityParticipantRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;

@Service
public class DisplaySessionService {

    private static final Duration MAX_TTL = Duration.ofHours(8);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);
    private static final String ROUTE =
        "/classrooms/{classroom_id}/guided-activities/{resource_id}/display-session-commands";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ActivitySessionRepository activityRepository;
    private final ActivityParticipantRepository participantRepository;
    private final DisplaySessionStore store;
    private final AuditTrailService auditTrailService;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public DisplaySessionService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ActivitySessionRepository activityRepository,
        ActivityParticipantRepository participantRepository,
        DisplaySessionStore store,
        AuditTrailService auditTrailService,
        IdempotencyRecordRepository idempotencyRepository,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.participantRepository = participantRepository;
        this.store = store;
        this.auditTrailService = auditTrailService;
        this.idempotencyRepository = idempotencyRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public DisplaySessionResult execute(DisplaySessionCommand command) {
        validate(command);
        authorizationService.authorize(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.CONTROL_PUBLIC_DISPLAY,
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
        DisplaySessionResult replay = replay(command, teacher.getId(), requestHash);
        if (replay != null) {
            return replay;
        }
        ActivitySession activity = activityRepository
            .findByClassroomIdAndId(command.classroomId(), command.activityId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The guided activity could not be found."
            ));
        if (
            command.expectedStateVersion() != null
                && command.expectedStateVersion() != activity.getStateVersion()
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The guided activity state has changed."
            );
        }
        if (command.command() == DisplaySessionCommandType.DISCONNECT) {
            if (!store.revoke(command.displaySessionId())) {
                throw operation(
                    HttpStatus.CONFLICT,
                    "SESSION_REVOKED",
                    "The display session is already disconnected or expired."
                );
            }
            auditTrailService.record(
                command.classroomId(),
                teacher.getId(),
                "PUBLIC_DISPLAY_DISCONNECTED",
                "ACTIVITY",
                command.activityId(),
                command.requestId(),
                command.correlationId(),
                Map.of(
                    "display_session_id", command.displaySessionId().toString(),
                    "status", "CONNECTED"
                ),
                Map.of("status", "DISCONNECTED"),
                "PUBLIC_DISPLAY",
                "교실 공용 화면 연결이 종료되었습니다."
            );
            DisplaySessionResult result = new DisplaySessionResult(
                command.displaySessionId(),
                null,
                null,
                "DISCONNECTED"
            );
            remember(command, teacher.getId(), requestHash, result, clock.instant());
            return result;
        }
        if (!"LIVE".equals(activity.getStatus())) {
            throw operation(
                HttpStatus.CONFLICT,
                "ACTIVITY_NOT_LIVE",
                "A public display can connect only to a live activity."
            );
        }
        if (!activity.isPublicDisplayEnabled()) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "PUBLIC_DATA_ONLY",
                "Public display is disabled for this activity."
            );
        }
        Instant now = clock.instant();
        Instant expiresAt = now.plus(MAX_TTL);
        UUID displaySessionId = uuidGenerator.generate();
        String token = newToken();
        store.save(
            sha256(token),
            new DisplaySessionRecord(
                displaySessionId,
                command.classroomId(),
                command.activityId(),
                activity.getPublicFields(),
                teacher.getId(),
                now,
                expiresAt
            )
        );
        auditTrailService.record(
            command.classroomId(),
            teacher.getId(),
            "PUBLIC_DISPLAY_CONNECTED",
            "ACTIVITY",
            command.activityId(),
            command.requestId(),
            command.correlationId(),
            Map.of("status", "DISCONNECTED"),
            Map.of(
                "display_session_id", displaySessionId.toString(),
                "status", "CONNECTED",
                "expires_at", expiresAt.toString()
            ),
            "PUBLIC_DISPLAY",
            "교실 공용 화면이 연결되었습니다."
        );
        DisplaySessionResult result = new DisplaySessionResult(
            displaySessionId,
            token,
            expiresAt,
            "CONNECTED"
        );
        remember(command, teacher.getId(), requestHash, result, now);
        return result;
    }

    @Transactional(readOnly = true)
    public PublicDisplayView bootstrap(UUID displaySessionId, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A display token is required."
            );
        }
        DisplaySessionRecord session = store
            .find(displaySessionId, sha256(rawToken))
            .orElseThrow(() -> operation(
                HttpStatus.UNAUTHORIZED,
                "SESSION_REVOKED",
                "The display session is invalid, expired, or disconnected."
            ));
        ActivitySession activity = activityRepository
            .findByClassroomIdAndId(session.classroomId(), session.activityId())
            .filter(candidate -> "LIVE".equals(candidate.getStatus()))
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "ACTIVITY_NOT_LIVE",
                "The linked activity is no longer live."
            ));
        long participantCount = participantRepository
            .countByClassroomIdAndActivitySessionId(
                session.classroomId(),
                session.activityId()
            );
        Map<String, Object> publicData = session.publicFields()
            .contains("anonymous_counts")
            ? Map.of(
                "activity_type", activity.getActivityType(),
                "status", activity.getStatus(),
                "participant_count", participantCount
            )
            : Map.of(
                "activity_type", activity.getActivityType(),
                "status", activity.getStatus()
            );
        RealtimeEventPublisher.requirePublicSafe(publicData);
        return new PublicDisplayView(
            activity.getId(),
            "LIVE",
            clock.instant(),
            activity.getEndsAt(),
            publicData
        );
    }

    public void revokeAllForActivity(UUID activityId) {
        store.revokeAllForActivity(activityId);
    }

    private DisplaySessionResult replay(
        DisplaySessionCommand command,
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
        Object expiresAt = body.get("expires_at");
        return new DisplaySessionResult(
            UUID.fromString(body.get("display_session_id").toString()),
            null,
            expiresAt == null || expiresAt.toString().isBlank()
                ? null
                : Instant.parse(expiresAt.toString()),
            body.get("status").toString()
        );
    }

    private void remember(
        DisplaySessionCommand command,
        UUID actorId,
        String requestHash,
        DisplaySessionResult result,
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
                    "display_session_id", result.displaySessionId(),
                    "expires_at",
                    result.expiresAt() == null ? "" : result.expiresAt().toString(),
                    "status", result.status()
                ),
                null,
                now.plus(IDEMPOTENCY_TTL),
                now
            )
        );
    }

    private static void validate(DisplaySessionCommand command) {
        if (
            command == null
                || command.classroomId() == null
                || command.activityId() == null
                || command.requestId() == null
                || command.command() == null
                || command.command() == DisplaySessionCommandType.CONNECT
                    && command.displaySessionId() != null
                || command.command() == DisplaySessionCommandType.DISCONNECT
                    && command.displaySessionId() == null
        ) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Check the display session command values."
            );
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(DisplaySessionCommand command) {
        return sha256(
            command.classroomId() + "|" + command.activityId() + "|"
                + command.command() + "|" + command.displaySessionId() + "|"
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
            throw new IllegalStateException("Could not hash the display token.", exception);
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
