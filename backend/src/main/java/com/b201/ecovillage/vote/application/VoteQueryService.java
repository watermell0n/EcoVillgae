package com.b201.ecovillage.vote.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.identity.authorization.AuthorizationDecision;
import com.b201.ecovillage.identity.authorization.ClassroomActorRole;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.TransactionBusinessRef;
import com.b201.ecovillage.ledger.domain.TransactionGroup;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ledger.repository.TransactionBusinessRefRepository;
import com.b201.ecovillage.ledger.repository.TransactionGroupRepository;
import com.b201.ecovillage.vote.domain.Vote;
import com.b201.ecovillage.vote.domain.VoteBallot;
import com.b201.ecovillage.vote.domain.VoteEligibility;
import com.b201.ecovillage.vote.domain.VoteOption;
import com.b201.ecovillage.vote.domain.VoteStatus;
import com.b201.ecovillage.vote.repository.VoteBallotRepository;
import com.b201.ecovillage.vote.repository.VoteEligibilityRepository;
import com.b201.ecovillage.vote.repository.VoteOptionRepository;
import com.b201.ecovillage.vote.repository.VoteRepository;

@Service
public class VoteQueryService {

    private static final String CLASSROOM_FUND_KEY = "CLASSROOM_FUND";

    private final ClassroomAuthorizationService authorizationService;
    private final VoteRepository voteRepository;
    private final VoteOptionRepository optionRepository;
    private final VoteEligibilityRepository eligibilityRepository;
    private final VoteBallotRepository ballotRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final TransactionBusinessRefRepository businessRefRepository;
    private final TransactionGroupRepository transactionGroupRepository;

    public VoteQueryService(
        ClassroomAuthorizationService authorizationService,
        VoteRepository voteRepository,
        VoteOptionRepository optionRepository,
        VoteEligibilityRepository eligibilityRepository,
        VoteBallotRepository ballotRepository,
        LedgerAccountRepository ledgerAccountRepository,
        TransactionBusinessRefRepository businessRefRepository,
        TransactionGroupRepository transactionGroupRepository
    ) {
        this.authorizationService = authorizationService;
        this.voteRepository = voteRepository;
        this.optionRepository = optionRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.ballotRepository = ballotRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.businessRefRepository = businessRefRepository;
        this.transactionGroupRepository = transactionGroupRepository;
    }

    @Transactional(readOnly = true)
    public VoteView get(String subject, UUID classroomId, UUID voteId) {
        UUID possibleStudentId = uuid(subject);
        AuthorizationDecision authorization = authorizationService.authorize(
            subject,
            classroomId,
            ClassroomPermission.READ_PUBLIC_FUND_VOTE,
            possibleStudentId
        );
        Vote vote = voteRepository.findByClassroomIdAndId(classroomId, voteId)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The vote could not be found."
            ));
        List<VoteOption> options = optionRepository
            .findAllByClassroomIdAndVoteIdOrderBySortOrder(classroomId, voteId);
        List<VoteBallot> ballots = ballotRepository.findAllByClassroomIdAndVoteId(
            classroomId,
            voteId
        );
        Map<UUID, Long> counts = counts(ballots);
        boolean countsVisible = vote.getStatus() == VoteStatus.CLOSED
            || vote.getStatus() == VoteStatus.RESULT_PUBLISHED
            || authorization.actorType() != ClassroomActorRole.STU;
        VoteEligibility eligibility = authorization.actorType() == ClassroomActorRole.STU
            ? eligibilityRepository
                .findByClassroomIdAndVoteIdAndClassroomStudentId(
                    classroomId,
                    voteId,
                    possibleStudentId
                )
                .orElse(null)
            : null;
        TransactionBusinessRef businessRef = businessRefRepository
            .findTopByClassroomIdAndRefTypeAndRefIdOrderByCreatedAtDesc(
                classroomId,
                "PUBLIC_FUND_VOTE",
                voteId
            )
            .orElse(null);
        TransactionGroup transaction = businessRef == null
            ? null
            : transactionGroupRepository.findById(
                businessRef.getTransactionGroupId()
            ).orElse(null);
        VoteOption selected = vote.getStatus() == VoteStatus.RESULT_PUBLISHED
            ? winner(options, counts)
            : null;
        long fundBalance = ledgerAccountRepository
            .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                classroomId,
                CLASSROOM_FUND_KEY,
                LedgerAccountType.CLASSROOM_FUND,
                LedgerAccountStatus.ACTIVE
            )
            .map(LedgerAccount::getCurrentBalance)
            .orElse(0L);
        return new VoteView(
            vote.getId(),
            vote.getTitle(),
            vote.getDescription(),
            vote.isAnonymous(),
            vote.getStatus().name(),
            vote.getStateVersion(),
            vote.getOpensAt(),
            vote.getClosesAt(),
            fundBalance,
            eligibilityRepository.countByClassroomIdAndVoteId(classroomId, voteId),
            ballots.size(),
            authorization.actorType() == ClassroomActorRole.STU
                ? eligibility != null
                : null,
            authorization.actorType() == ClassroomActorRole.STU
                ? eligibility != null && eligibility.getUsedAt() != null
                : null,
            selected == null ? null : selected.getId(),
            selected == null
                ? null
                : transaction == null ? selected.getLabel() : transaction.getReason(),
            businessRef == null ? null : businessRef.getTransactionGroupId(),
            options.stream()
                .map(option -> new VoteOptionView(
                    option.getId(),
                    option.getLabel(),
                    option.getDescription(),
                    option.getBudgetAmount(),
                    countsVisible
                        ? counts.getOrDefault(option.getId(), 0L)
                        : null
                ))
                .toList()
        );
    }

    private static Map<UUID, Long> counts(List<VoteBallot> ballots) {
        Map<UUID, Long> result = new LinkedHashMap<>();
        ballots.forEach(ballot -> result.merge(
            ballot.getVoteOptionId(),
            1L,
            Long::sum
        ));
        return result;
    }

    private static VoteOption winner(
        List<VoteOption> options,
        Map<UUID, Long> counts
    ) {
        return options.stream()
            .max((left, right) -> {
                int byCount = Long.compare(
                    counts.getOrDefault(left.getId(), 0L),
                    counts.getOrDefault(right.getId(), 0L)
                );
                return byCount != 0
                    ? byCount
                    : Integer.compare(right.getSortOrder(), left.getSortOrder());
            })
            .orElse(null);
    }

    private static UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
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
