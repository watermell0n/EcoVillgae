package com.b201.ecovillage.realtime.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.ai.repository.AsyncOperationRepository;
import com.b201.ecovillage.quiz.repository.ActivityParticipantRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;
import com.b201.ecovillage.quiz.repository.QuizSessionRepository;
import com.b201.ecovillage.vote.repository.VoteRepository;

@Service
public class RealtimeResourceAuthorizationService {

    private final ActivitySessionRepository activityRepository;
    private final ActivityParticipantRepository participantRepository;
    private final QuizSessionRepository quizRepository;
    private final VoteRepository voteRepository;
    private final AsyncOperationRepository operationRepository;

    public RealtimeResourceAuthorizationService(
        ActivitySessionRepository activityRepository,
        ActivityParticipantRepository participantRepository,
        QuizSessionRepository quizRepository,
        VoteRepository voteRepository,
        AsyncOperationRepository operationRepository
    ) {
        this.activityRepository = activityRepository;
        this.participantRepository = participantRepository;
        this.quizRepository = quizRepository;
        this.voteRepository = voteRepository;
        this.operationRepository = operationRepository;
    }

    @Transactional(readOnly = true)
    public void requireAuthorized(
        RealtimeTicket ticket,
        RealtimeChannel channel,
        UUID resourceId
    ) {
        boolean authorized = switch (channel) {
            // 잠금 없는 조회를 쓴다. 쓰기 잠금 finder는 읽기 전용 트랜잭션에서
            // PostgreSQL이 거절해 활동 채널 구독이 늘 INVALID_MESSAGE로 실패했다.
            case ACTIVITY -> activityRepository
                .findOneByClassroomIdAndId(ticket.classroomId(), resourceId)
                .filter(activity -> participantAllowed(ticket, resourceId))
                .isPresent();
            case QUIZ -> quizRepository
                .findByClassroomIdAndId(ticket.classroomId(), resourceId)
                .filter(quiz -> participantAllowed(ticket, resourceId))
                .isPresent();
            case VOTE -> voteRepository
                .findByClassroomIdAndId(ticket.classroomId(), resourceId)
                .filter(vote -> participantAllowed(ticket, resourceId))
                .isPresent();
            case OPERATION -> operationRepository
                .findByClassroomIdAndId(ticket.classroomId(), resourceId)
                .filter(operation -> "TCH".equals(ticket.role())
                    || ticket.actorId().equals(operation.getRequestedByUserId()))
                .isPresent();
            default -> true;
        };
        if (!authorized) {
            throw new RealtimeProtocolException(
                "RESOURCE_SCOPE_VIOLATION",
                "The realtime resource is not available in this classroom scope"
            );
        }
    }

    private boolean participantAllowed(RealtimeTicket ticket, UUID resourceId) {
        return "TCH".equals(ticket.role())
            || participantRepository
                .existsByClassroomIdAndActivitySessionIdAndClassroomStudentId(
                    ticket.classroomId(),
                    resourceId,
                    ticket.actorId()
                );
    }
}
