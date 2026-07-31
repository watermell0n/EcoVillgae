package com.b201.ecovillage.classroom.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.b201.ecovillage.audit.application.AuditTrailService;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;

class AccessModeCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    @Test
    void opensManualAutonomousAccessForDefaultThirtyMinutes() {
        ClassroomAuthorizationService authorization =
            mock(ClassroomAuthorizationService.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        ClassroomRepository classrooms = mock(ClassroomRepository.class);
        ClassroomPolicyVersionRepository policies =
            mock(ClassroomPolicyVersionRepository.class);
        ClassroomModuleRepository modules = mock(ClassroomModuleRepository.class);
        ActivitySessionRepository activities =
            mock(ActivitySessionRepository.class);
        IdempotencyRecordRepository idempotency =
            mock(IdempotencyRecordRepository.class);
        RealtimeEventPublisher events = mock(RealtimeEventPublisher.class);
        AuditTrailService audits = mock(AuditTrailService.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        UuidV7Generator ids = new UuidV7Generator(clock, new SecureRandom());
        AccessModeCommandService service = new AccessModeCommandService(
            authorization,
            users,
            classrooms,
            policies,
            modules,
            activities,
            idempotency,
            events,
            audits,
            ids,
            clock
        );
        UUID classroomId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Classroom classroom = Classroom.active(classroomId, "ABCD1234", NOW);
        when(users.findByExternalSubject("teacher")).thenReturn(Optional.of(
            UserAccount.createTeacher(teacherId, "teacher", "Teacher", NOW)
        ));
        when(idempotency.findForReplay(
            classroomId,
            teacherId,
            "/classrooms/{classroom_id}/access-mode-commands",
            requestId.toString()
        )).thenReturn(Optional.empty());
        when(classrooms.findById(classroomId)).thenReturn(Optional.of(classroom));
        when(policies.findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
            classroomId,
            "ACCESS_MODE"
        )).thenReturn(Optional.empty());
        when(modules.findAllByClassroomIdAndStatusOrderByModuleCode(
            classroomId,
            "ACTIVE"
        )).thenReturn(List.of());
        when(classrooms.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        AccessModeResult result = service.execute(new AccessModeCommand(
            "teacher",
            classroomId,
            requestId,
            UUID.randomUUID(),
            0L,
            AccessModeCommandType.OPEN,
            null
        ));

        assertEquals(AppAccessMode.AUTONOMOUS, result.mode());
        assertEquals(NOW.plusSeconds(30 * 60), result.expiresAt());
        assertEquals("AUTONOMOUS", classroom.getAppModeOverride());
        verify(policies).save(any());
        verify(idempotency).save(any());
    }

    @Test
    void closesManualAccessWithoutExpiry() {
        ClassroomAuthorizationService authorization =
            mock(ClassroomAuthorizationService.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        ClassroomRepository classrooms = mock(ClassroomRepository.class);
        ClassroomPolicyVersionRepository policies =
            mock(ClassroomPolicyVersionRepository.class);
        ClassroomModuleRepository modules = mock(ClassroomModuleRepository.class);
        ActivitySessionRepository activities =
            mock(ActivitySessionRepository.class);
        IdempotencyRecordRepository idempotency =
            mock(IdempotencyRecordRepository.class);
        RealtimeEventPublisher events = mock(RealtimeEventPublisher.class);
        AuditTrailService audits = mock(AuditTrailService.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        UuidV7Generator ids = new UuidV7Generator(clock, new SecureRandom());
        AccessModeCommandService service = new AccessModeCommandService(
            authorization,
            users,
            classrooms,
            policies,
            modules,
            activities,
            idempotency,
            events,
            audits,
            ids,
            clock
        );
        UUID classroomId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Classroom classroom = Classroom.active(classroomId, "ABCD1234", NOW);
        when(users.findByExternalSubject("teacher")).thenReturn(Optional.of(
            UserAccount.createTeacher(teacherId, "teacher", "Teacher", NOW)
        ));
        when(idempotency.findForReplay(
            classroomId,
            teacherId,
            "/classrooms/{classroom_id}/access-mode-commands",
            requestId.toString()
        )).thenReturn(Optional.empty());
        when(classrooms.findById(classroomId)).thenReturn(Optional.of(classroom));
        when(policies.findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
            classroomId,
            "ACCESS_MODE"
        )).thenReturn(Optional.empty());
        when(classrooms.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        AccessModeResult result = service.execute(new AccessModeCommand(
            "teacher",
            classroomId,
            requestId,
            UUID.randomUUID(),
            0L,
            AccessModeCommandType.CLOSE,
            null
        ));

        assertEquals(AppAccessMode.CLOSED, result.mode());
        assertNull(result.expiresAt());
        assertEquals("CLOSED", classroom.getAppModeOverride());
    }
}
