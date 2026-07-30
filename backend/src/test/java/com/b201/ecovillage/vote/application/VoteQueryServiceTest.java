package com.b201.ecovillage.vote.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.b201.ecovillage.identity.authorization.AuthorizationDecision;
import com.b201.ecovillage.identity.authorization.AuthorizationScope;
import com.b201.ecovillage.identity.authorization.ClassroomActorRole;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ledger.repository.TransactionBusinessRefRepository;
import com.b201.ecovillage.ledger.repository.TransactionGroupRepository;
import com.b201.ecovillage.vote.domain.Vote;
import com.b201.ecovillage.vote.domain.VoteBallot;
import com.b201.ecovillage.vote.domain.VoteEligibility;
import com.b201.ecovillage.vote.domain.VoteOption;
import com.b201.ecovillage.vote.repository.VoteBallotRepository;
import com.b201.ecovillage.vote.repository.VoteEligibilityRepository;
import com.b201.ecovillage.vote.repository.VoteOptionRepository;
import com.b201.ecovillage.vote.repository.VoteRepository;

class VoteQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    @Test
    void hidesLiveOptionCountsAndIndividualChoiceFromStudents() {
        UUID classroomId = UUID.randomUUID();
        UUID voteId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID firstOptionId = UUID.randomUUID();
        UUID secondOptionId = UUID.randomUUID();
        ClassroomAuthorizationService authorizationService =
            mock(ClassroomAuthorizationService.class);
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteOptionRepository optionRepository = mock(VoteOptionRepository.class);
        VoteEligibilityRepository eligibilityRepository =
            mock(VoteEligibilityRepository.class);
        VoteBallotRepository ballotRepository =
            mock(VoteBallotRepository.class);
        LedgerAccountRepository accountRepository =
            mock(LedgerAccountRepository.class);
        TransactionBusinessRefRepository businessRefRepository =
            mock(TransactionBusinessRefRepository.class);
        TransactionGroupRepository transactionRepository =
            mock(TransactionGroupRepository.class);
        VoteQueryService service = new VoteQueryService(
            authorizationService,
            voteRepository,
            optionRepository,
            eligibilityRepository,
            ballotRepository,
            accountRepository,
            businessRefRepository,
            transactionRepository
        );
        Vote vote = Vote.ready(
            voteId,
            classroomId,
            "공동기금 사용처",
            null,
            NOW.minusSeconds(60)
        );
        vote.open(NOW.minusSeconds(30));
        List<VoteOption> options = List.of(
            VoteOption.create(
                firstOptionId,
                classroomId,
                voteId,
                "학급 도서",
                null,
                0,
                100,
                NOW.minusSeconds(60)
            ),
            VoteOption.create(
                secondOptionId,
                classroomId,
                voteId,
                "체육 용품",
                null,
                1,
                200,
                NOW.minusSeconds(60)
            )
        );
        VoteEligibility eligibility = VoteEligibility.create(
            UUID.randomUUID(),
            classroomId,
            voteId,
            studentId,
            "eligibility-token-hash",
            NOW.minusSeconds(60)
        );
        eligibility.use(NOW.minusSeconds(5));
        when(authorizationService.authorize(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq(classroomId),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(studentId)
        )).thenReturn(new AuthorizationDecision(
            "ALLOW",
            "PM-ACT-001",
            ClassroomActorRole.STU,
            AuthorizationScope.SELF,
            voteId,
            0,
            null,
            NOW
        ));
        when(voteRepository.findByClassroomIdAndId(classroomId, voteId))
            .thenReturn(Optional.of(vote));
        when(optionRepository.findAllByClassroomIdAndVoteIdOrderBySortOrder(
            classroomId,
            voteId
        )).thenReturn(options);
        when(ballotRepository.findAllByClassroomIdAndVoteId(classroomId, voteId))
            .thenReturn(List.of(VoteBallot.cast(
                UUID.randomUUID(),
                classroomId,
                voteId,
                firstOptionId,
                "eligibility-token-hash",
                "receipt-hash",
                NOW.minusSeconds(5)
            )));
        when(eligibilityRepository
            .findByClassroomIdAndVoteIdAndClassroomStudentId(
                classroomId,
                voteId,
                studentId
            )).thenReturn(Optional.of(eligibility));
        when(eligibilityRepository.countByClassroomIdAndVoteId(
            classroomId,
            voteId
        )).thenReturn(2L);
        when(businessRefRepository
            .findTopByClassroomIdAndRefTypeAndRefIdOrderByCreatedAtDesc(
                classroomId,
                "PUBLIC_FUND_VOTE",
                voteId
            )).thenReturn(Optional.empty());
        when(accountRepository
            .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                org.mockito.ArgumentMatchers.eq(classroomId),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
            )).thenReturn(Optional.empty());

        VoteView result = service.get(
            studentId.toString(),
            classroomId,
            voteId
        );

        assertTrue(result.eligible());
        assertTrue(result.hasVoted());
        assertEquals(1, result.ballotCount());
        assertNull(result.selectedOptionId());
        assertNull(result.selectedUse());
        assertNull(result.options().get(0).voteCount());
        assertNull(result.options().get(1).voteCount());
        assertFalse(result.options().stream().anyMatch(
            option -> option.optionId() == null
        ));
    }
}
