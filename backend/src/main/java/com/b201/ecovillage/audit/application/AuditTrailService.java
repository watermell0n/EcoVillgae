package com.b201.ecovillage.audit.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.audit.domain.AuditEvent;
import com.b201.ecovillage.audit.repository.AuditEventRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;

@Service
public class AuditTrailService {

    private static final Duration RETENTION = Duration.ofDays(365L * 7);

    private final AuditEventRepository auditRepository;
    private final ClassroomAuthorizationService authorizationService;
    private final RealtimeEventPublisher eventPublisher;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public AuditTrailService(
        AuditEventRepository auditRepository,
        ClassroomAuthorizationService authorizationService,
        RealtimeEventPublisher eventPublisher,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.auditRepository = auditRepository;
        this.authorizationService = authorizationService;
        this.eventPublisher = eventPublisher;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public UUID record(
        UUID classroomId,
        UUID actorId,
        String action,
        String targetType,
        UUID targetId,
        UUID requestId,
        UUID correlationId,
        Map<String, Object> beforeSummary,
        Map<String, Object> afterSummary,
        String notificationCategory,
        String notificationTitle
    ) {
        Instant now = clock.instant();
        AuditEvent event = auditRepository.save(
            AuditEvent.record(
                uuidGenerator.generate(),
                classroomId,
                "TEACHER",
                actorId,
                action,
                targetType,
                targetId,
                requestId == null ? null : requestId.toString(),
                correlationId == null ? null : correlationId.toString(),
                beforeSummary,
                afterSummary,
                now,
                now.plus(RETENTION)
            )
        );
        if (notificationTitle != null && !notificationTitle.isBlank()) {
            UUID notificationId = uuidGenerator.generate();
            eventPublisher.publish(
                classroomId,
                "TEACHER_CONTROL",
                null,
                "notification.created",
                targetType,
                targetId,
                0,
                Map.of(
                    "notification_id", notificationId.toString(),
                    "category", notificationCategory,
                    "title", notificationTitle,
                    "resource_id", targetId.toString()
                ),
                false
            );
        }
        return event.getId();
    }

    @Transactional(readOnly = true)
    public List<AuditEventView> findAll(
        String teacherSubject,
        UUID classroomId,
        int limit
    ) {
        authorizationService.authorize(
            teacherSubject,
            classroomId,
            ClassroomPermission.READ_AUDIT_EVENTS,
            null
        );
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return auditRepository
            .findAllByClassroomIdOrderByOccurredAtDescIdDesc(
                classroomId,
                PageRequest.of(0, boundedLimit)
            )
            .stream()
            .map(event -> new AuditEventView(
                event.getId(),
                event.getActorType(),
                event.getActorId(),
                event.getAction(),
                event.getTargetType(),
                event.getTargetId(),
                event.getRequestId(),
                event.getCorrelationId(),
                event.getBeforeSummary(),
                event.getAfterSummary(),
                event.getOccurredAt()
            ))
            .toList();
    }
}
