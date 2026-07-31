package com.b201.ecovillage.audit.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.b201.ecovillage.audit.repository.AuditEventRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;

class AuditTrailServiceTest {

    @Test
    void writesImmutableAuditAndTeacherNotificationTogether() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        ClassroomAuthorizationService authorization =
            mock(ClassroomAuthorizationService.class);
        RealtimeEventPublisher events = mock(RealtimeEventPublisher.class);
        Clock clock = Clock.fixed(
            Instant.parse("2026-07-30T03:00:00Z"),
            ZoneOffset.UTC
        );
        AuditTrailService service = new AuditTrailService(
            repository,
            authorization,
            events,
            new UuidV7Generator(clock, new SecureRandom()),
            clock
        );
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        UUID classroomId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();

        service.record(
            classroomId,
            UUID.randomUUID(),
            "GUIDED_ACTIVITY_STARTED",
            "ACTIVITY",
            activityId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Map.of("status", "READY"),
            Map.of("status", "LIVE"),
            "ACTIVITY",
            "지도 활동이 시작되었습니다."
        );

        verify(repository).save(any());
        verify(events).publish(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyLong(),
            any(),
            anyBoolean()
        );
    }
}
