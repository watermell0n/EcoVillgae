package com.b201.ecovillage.quiz.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.classroom.application.EffectiveAccessModeService;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.quiz.domain.ActivitySession;
import com.b201.ecovillage.quiz.repository.ActivityParticipantRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;
import com.b201.ecovillage.realtime.application.DisplaySessionService;

class GuidedActivityCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    @Test
    void startsReadyActivityWithActiveParticipants() {
        ClassroomAuthorizationService authorization =
            mock(ClassroomAuthorizationService.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        ClassroomRepository classrooms = mock(ClassroomRepository.class);
        ClassroomStudentRepository students =
            mock(ClassroomStudentRepository.class);
        ActivitySessionRepository activities =
            mock(ActivitySessionRepository.class);
        ActivityParticipantRepository participants =
            mock(ActivityParticipantRepository.class);
        IdempotencyRecordRepository idempotency =
            mock(IdempotencyRecordRepository.class);
        RealtimeEventPublisher events = mock(RealtimeEventPublisher.class);
        EffectiveAccessModeService accessModes = mock(EffectiveAccessModeService.class);
        DisplaySessionService displaySessions = mock(DisplaySessionService.class);
        AuditTrailService audits = mock(AuditTrailService.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        UuidV7Generator ids = new UuidV7Generator(clock, new SecureRandom());
        GuidedActivityCommandService service = new GuidedActivityCommandService(
            authorization,
            users,
            classrooms,
            students,
            activities,
            participants,
            idempotency,
            events,
            accessModes,
            displaySessions,
            audits,
            ids,
            clock
        );
        UUID classroomId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        ActivitySession activity = ActivitySession.ready(
            activityId,
            classroomId,
            "VOTE",
            teacherId,
            true,
            NOW.minusSeconds(60)
        );
        ClassroomStudent student = ClassroomStudent.enroll(
            studentId,
            classroomId,
            1,
            "student",
            NOW.minusSeconds(60)
        );
        when(users.findByExternalSubject("teacher")).thenReturn(Optional.of(
            UserAccount.createTeacher(teacherId, "teacher", "Teacher", NOW)
        ));
        when(idempotency.findForReplay(
            classroomId,
            teacherId,
            "/classrooms/{classroom_id}/guided-activities/{resource_id}/start-commands",
            requestId.toString()
        )).thenReturn(Optional.empty());
        when(classrooms.findForUpdateById(classroomId)).thenReturn(Optional.of(
            Classroom.active(classroomId, "ABCD1234", NOW)
        ));
        when(activities.findByClassroomIdAndId(classroomId, activityId))
            .thenReturn(Optional.of(activity));
        when(activities.findFirstByClassroomIdAndStatus(classroomId, "LIVE"))
            .thenReturn(Optional.empty());
        when(students.findAllForUpdate(
            classroomId,
            List.of(studentId),
            ClassroomStudentStatus.ACTIVE
        )).thenReturn(List.of(student));
        when(activities.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        GuidedActivityResult result = service.start(new GuidedActivityStartCommand(
            "teacher",
            classroomId,
            activityId,
            requestId,
            UUID.randomUUID(),
            0L,
            List.of(studentId),
            List.of("prompt", "timer", "anonymous_counts")
        ));

        assertEquals("LIVE", result.status());
        verify(participants).saveAll(any());
        verify(idempotency).save(any());
    }
}
