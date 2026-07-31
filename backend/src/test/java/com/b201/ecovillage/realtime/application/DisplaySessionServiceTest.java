package com.b201.ecovillage.realtime.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.b201.ecovillage.audit.application.AuditTrailService;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.quiz.domain.ActivitySession;
import com.b201.ecovillage.quiz.repository.ActivityParticipantRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;

class DisplaySessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    @Test
    void connectsWithHashedRedisCredentialAndOneTimeResponseToken() {
        ClassroomAuthorizationService authorization =
            mock(ClassroomAuthorizationService.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        ActivitySessionRepository activities =
            mock(ActivitySessionRepository.class);
        ActivityParticipantRepository participants =
            mock(ActivityParticipantRepository.class);
        DisplaySessionStore store = mock(DisplaySessionStore.class);
        AuditTrailService audits = mock(AuditTrailService.class);
        IdempotencyRecordRepository idempotency =
            mock(IdempotencyRecordRepository.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        UuidV7Generator ids = new UuidV7Generator(clock, new SecureRandom());
        DisplaySessionService service = new DisplaySessionService(
            authorization,
            users,
            activities,
            participants,
            store,
            audits,
            idempotency,
            ids,
            clock
        );
        UUID classroomId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        ActivitySession activity = ActivitySession.liveQuiz(
            activityId,
            classroomId,
            teacherId,
            true,
            NOW
        );
        when(users.findByExternalSubject("teacher")).thenReturn(Optional.of(
            UserAccount.createTeacher(teacherId, "teacher", "Teacher", NOW)
        ));
        when(activities.findByClassroomIdAndId(classroomId, activityId))
            .thenReturn(Optional.of(activity));
        when(idempotency.findForReplay(
            any(),
            any(),
            any(),
            any()
        )).thenReturn(Optional.empty());

        DisplaySessionResult result = service.execute(new DisplaySessionCommand(
            "teacher",
            classroomId,
            activityId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            0L,
            DisplaySessionCommandType.CONNECT,
            null
        ));

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(store).save(hash.capture(), any(DisplaySessionRecord.class));
        assertEquals(64, hash.getValue().length());
        assertNotEquals(hash.getValue(), result.displayToken());
        assertFalse(result.displayToken().isBlank());
        assertEquals("CONNECTED", result.status());
        assertEquals(NOW.plusSeconds(8 * 60 * 60), result.expiresAt());
    }
}
