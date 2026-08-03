package com.b201.ecovillage.realtime.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.b201.ecovillage.ai.repository.AsyncOperationRepository;
import com.b201.ecovillage.quiz.domain.ActivitySession;
import com.b201.ecovillage.quiz.repository.ActivityParticipantRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;
import com.b201.ecovillage.quiz.repository.QuizSessionRepository;
import com.b201.ecovillage.vote.repository.VoteRepository;

class RealtimeResourceAuthorizationServiceTest {

    private final ActivitySessionRepository activityRepository =
        mock(ActivitySessionRepository.class);
    private final ActivityParticipantRepository participantRepository =
        mock(ActivityParticipantRepository.class);
    private final QuizSessionRepository quizRepository = mock(QuizSessionRepository.class);
    private final VoteRepository voteRepository = mock(VoteRepository.class);
    private final AsyncOperationRepository operationRepository =
        mock(AsyncOperationRepository.class);
    private final RealtimeResourceAuthorizationService service =
        new RealtimeResourceAuthorizationService(
            activityRepository,
            participantRepository,
            quizRepository,
            voteRepository,
            operationRepository
        );

    @Test
    void acceptsActivityFromTicketClassroom() {
        RealtimeTicket ticket = ticket();
        UUID activityId = UUID.randomUUID();
        when(activityRepository.findByClassroomIdAndId(ticket.classroomId(), activityId))
            .thenReturn(Optional.of(mock(ActivitySession.class)));

        assertDoesNotThrow(() -> service.requireAuthorized(
            ticket,
            RealtimeChannel.ACTIVITY,
            activityId
        ));
    }

    @Test
    void rejectsResourceOutsideTicketClassroom() {
        RealtimeTicket ticket = ticket();
        UUID activityId = UUID.randomUUID();
        when(activityRepository.findByClassroomIdAndId(ticket.classroomId(), activityId))
            .thenReturn(Optional.empty());

        assertThrows(
            RealtimeProtocolException.class,
            () -> service.requireAuthorized(ticket, RealtimeChannel.ACTIVITY, activityId)
        );
    }

    @Test
    void rejectsStudentWhoIsNotAnActivityParticipant() {
        RealtimeTicket ticket = studentTicket();
        UUID activityId = UUID.randomUUID();
        when(activityRepository.findByClassroomIdAndId(ticket.classroomId(), activityId))
            .thenReturn(Optional.of(mock(ActivitySession.class)));
        when(participantRepository
            .existsByClassroomIdAndActivitySessionIdAndClassroomStudentId(
                ticket.classroomId(),
                activityId,
                ticket.actorId()
            ))
            .thenReturn(false);

        assertThrows(
            RealtimeProtocolException.class,
            () -> service.requireAuthorized(ticket, RealtimeChannel.ACTIVITY, activityId)
        );
    }

    private static RealtimeTicket ticket() {
        UUID classroomId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        return new RealtimeTicket(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "teacher-subject",
            actorId,
            "TCH",
            classroomId,
            Set.of("WS-ACT-001"),
            Set.of(classroomId, actorId),
            Instant.now().plusSeconds(60)
        );
    }

    private static RealtimeTicket studentTicket() {
        UUID classroomId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        return new RealtimeTicket(
            UUID.randomUUID(),
            UUID.randomUUID(),
            actorId.toString(),
            actorId,
            "STU",
            classroomId,
            Set.of("WS-ACT-001"),
            Set.of(classroomId, actorId),
            Instant.now().plusSeconds(60)
        );
    }
}
