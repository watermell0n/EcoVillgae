package com.b201.ecovillage.vote.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;
import com.b201.ecovillage.vote.domain.Vote;
import com.b201.ecovillage.vote.domain.VoteBallot;
import com.b201.ecovillage.vote.domain.VoteEligibility;
import com.b201.ecovillage.vote.domain.VoteOption;
import com.b201.ecovillage.vote.repository.VoteBallotRepository;
import com.b201.ecovillage.vote.repository.VoteEligibilityRepository;
import com.b201.ecovillage.vote.repository.VoteOptionRepository;
import com.b201.ecovillage.vote.repository.VoteRepository;

class VoteBallotServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    private final ClassroomAuthorizationService authorizationService =
        mock(ClassroomAuthorizationService.class);
    private final VoteRepository voteRepository = mock(VoteRepository.class);
    private final VoteOptionRepository optionRepository =
        mock(VoteOptionRepository.class);
    private final VoteEligibilityRepository eligibilityRepository =
        mock(VoteEligibilityRepository.class);
    private final VoteBallotRepository ballotRepository =
        mock(VoteBallotRepository.class);
    private final IdempotencyRecordRepository idempotencyRepository =
        mock(IdempotencyRecordRepository.class);
    private final RealtimeEventPublisher eventPublisher =
        mock(RealtimeEventPublisher.class);
    private final UuidV7Generator uuidGenerator = mock(UuidV7Generator.class);

    private VoteBallotService service;

    @BeforeEach
    void setUp() {
        service = new VoteBallotService(
            authorizationService,
            voteRepository,
            optionRepository,
            eligibilityRepository,
            ballotRepository,
            idempotencyRepository,
            eventPublisher,
            uuidGenerator,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void storesOnlyAnAnonymousBallotAndConsumesEligibility() {
        UUID classroomId = UUID.randomUUID();
        UUID voteId = UUID.randomUUID();
        UUID optionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Vote vote = Vote.ready(
            voteId,
            classroomId,
            "공동기금 사용처",
            null,
            NOW.minusSeconds(60)
        );
        vote.open(NOW.minusSeconds(30));
        VoteOption option = VoteOption.create(
            optionId,
            classroomId,
            voteId,
            "학급 도서",
            null,
            0,
            100,
            NOW.minusSeconds(60)
        );
        VoteEligibility eligibility = VoteEligibility.create(
            UUID.randomUUID(),
            classroomId,
            voteId,
            studentId,
            "eligibility-token-hash",
            NOW.minusSeconds(60)
        );
        VoteBallotCommand command = new VoteBallotCommand(
            studentId.toString(),
            classroomId,
            voteId,
            optionId,
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        when(idempotencyRepository.findForReplay(any(), any(), any(), any()))
            .thenReturn(Optional.empty());
        when(voteRepository.findForUpdateByClassroomIdAndId(classroomId, voteId))
            .thenReturn(Optional.of(vote));
        when(optionRepository.findByClassroomIdAndVoteIdAndId(
            classroomId,
            voteId,
            optionId
        )).thenReturn(Optional.of(option));
        when(eligibilityRepository
            .findForUpdateByClassroomIdAndVoteIdAndClassroomStudentId(
                classroomId,
                voteId,
                studentId
            )).thenReturn(Optional.of(eligibility));
        when(uuidGenerator.generate()).thenReturn(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        when(ballotRepository.findAllByClassroomIdAndVoteId(classroomId, voteId))
            .thenAnswer(invocation -> {
                ArgumentCaptor<VoteBallot> captor =
                    ArgumentCaptor.forClass(VoteBallot.class);
                verify(ballotRepository).save(captor.capture());
                return List.of(captor.getValue());
            });
        when(eligibilityRepository.countByClassroomIdAndVoteId(
            classroomId,
            voteId
        )).thenReturn(1L);

        VoteBallotResult result = service.cast(command);

        assertNotNull(result.receiptHash());
        assertEquals(NOW, result.castAt());
        assertEquals(NOW, eligibility.getUsedAt());
        verify(authorizationService).authorize(
            studentId.toString(),
            classroomId,
            ClassroomPermission.CAST_PUBLIC_FUND_VOTE,
            studentId
        );
        verify(eventPublisher).publish(
            eq(classroomId),
            eq("VOTE"),
            eq(voteId),
            eq("vote.ballot.aggregate_changed"),
            eq("VOTE"),
            eq(voteId),
            eq(vote.getStateVersion()),
            any(),
            eq(true)
        );
    }

    @Test
    void refusesASecondBallotForTheSameEligibility() {
        UUID classroomId = UUID.randomUUID();
        UUID voteId = UUID.randomUUID();
        UUID optionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Vote vote = Vote.ready(
            voteId,
            classroomId,
            "공동기금 사용처",
            null,
            NOW.minusSeconds(60)
        );
        vote.open(NOW.minusSeconds(30));
        VoteEligibility eligibility = VoteEligibility.create(
            UUID.randomUUID(),
            classroomId,
            voteId,
            studentId,
            "eligibility-token-hash",
            NOW.minusSeconds(60)
        );
        eligibility.use(NOW.minusSeconds(10));
        when(idempotencyRepository.findForReplay(any(), any(), any(), any()))
            .thenReturn(Optional.empty());
        when(voteRepository.findForUpdateByClassroomIdAndId(classroomId, voteId))
            .thenReturn(Optional.of(vote));
        when(optionRepository.findByClassroomIdAndVoteIdAndId(
            classroomId,
            voteId,
            optionId
        )).thenReturn(Optional.of(VoteOption.create(
            optionId,
            classroomId,
            voteId,
            "학급 도서",
            null,
            0,
            100,
            NOW.minusSeconds(60)
        )));
        when(eligibilityRepository
            .findForUpdateByClassroomIdAndVoteIdAndClassroomStudentId(
                classroomId,
                voteId,
                studentId
            )).thenReturn(Optional.of(eligibility));

        assertThrows(
            ClassroomOperationException.class,
            () -> service.cast(new VoteBallotCommand(
                studentId.toString(),
                classroomId,
                voteId,
                optionId,
                UUID.randomUUID(),
                UUID.randomUUID()
            ))
        );
        verify(ballotRepository, never()).save(any());
    }
}
