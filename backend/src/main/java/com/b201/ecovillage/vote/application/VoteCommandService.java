package com.b201.ecovillage.vote.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.audit.application.AuditTrailService;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStatus;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.application.AtomicLedgerTransactionService;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.application.LedgerDomainMutation;
import com.b201.ecovillage.ledger.application.LedgerTransactionCommand;
import com.b201.ecovillage.ledger.application.LedgerTransactionPosting;
import com.b201.ecovillage.ledger.application.LedgerTransactionResult;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.LedgerActorType;
import com.b201.ecovillage.ledger.domain.TransactionBusinessRef;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ledger.repository.TransactionBusinessRefRepository;
import com.b201.ecovillage.quiz.domain.ActivityParticipant;
import com.b201.ecovillage.quiz.domain.ActivitySession;
import com.b201.ecovillage.quiz.repository.ActivityParticipantRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;
import com.b201.ecovillage.realtime.application.DisplaySessionService;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;
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
public class VoteCommandService {

    private static final String ROUTE =
        "/classrooms/{classroom_id}/votes/{resource_id}/commands";
    private static final String CLASSROOM_FUND_KEY = "CLASSROOM_FUND";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);
    private static final Set<String> PUBLIC_VOTE_FIELDS = Set.of(
        "scene",
        "prompt",
        "anonymous_counts",
        "public_result"
    );

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRepository studentRepository;
    private final ActivitySessionRepository activityRepository;
    private final ActivityParticipantRepository participantRepository;
    private final VoteRepository voteRepository;
    private final VoteOptionRepository optionRepository;
    private final VoteEligibilityRepository eligibilityRepository;
    private final VoteBallotRepository ballotRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final TransactionBusinessRefRepository businessRefRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final AtomicLedgerTransactionService ledgerTransactionService;
    private final RealtimeEventPublisher eventPublisher;
    private final DisplaySessionService displaySessionService;
    private final AuditTrailService auditTrailService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public VoteCommandService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ClassroomRepository classroomRepository,
        ClassroomStudentRepository studentRepository,
        ActivitySessionRepository activityRepository,
        ActivityParticipantRepository participantRepository,
        VoteRepository voteRepository,
        VoteOptionRepository optionRepository,
        VoteEligibilityRepository eligibilityRepository,
        VoteBallotRepository ballotRepository,
        LedgerAccountRepository ledgerAccountRepository,
        TransactionBusinessRefRepository businessRefRepository,
        IdempotencyRecordRepository idempotencyRepository,
        AtomicLedgerTransactionService ledgerTransactionService,
        RealtimeEventPublisher eventPublisher,
        DisplaySessionService displaySessionService,
        AuditTrailService auditTrailService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.classroomRepository = classroomRepository;
        this.studentRepository = studentRepository;
        this.activityRepository = activityRepository;
        this.participantRepository = participantRepository;
        this.voteRepository = voteRepository;
        this.optionRepository = optionRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.ballotRepository = ballotRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.businessRefRepository = businessRefRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.ledgerTransactionService = ledgerTransactionService;
        this.eventPublisher = eventPublisher;
        this.displaySessionService = displaySessionService;
        this.auditTrailService = auditTrailService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public VoteCommandResult execute(VoteCommand command) {
        validateEnvelope(command);
        authorizationService.authorize(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.MANAGE_PUBLIC_FUND_VOTE,
            null
        );
        UserAccount teacher = userRepository
            .findByExternalSubject(command.teacherSubject())
            .orElseThrow(() -> operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A valid teacher session is required."
            ));
        String requestHash = hash(command);
        VoteCommandResult replay = replay(command, teacher.getId(), requestHash);
        if (replay != null) {
            return replay;
        }
        requireActiveClassroom(command.classroomId());
        return switch (command.command()) {
            case CREATE -> create(command, teacher, requestHash);
            case OPEN -> open(command, teacher, requestHash);
            case CLOSE -> close(command, teacher, requestHash);
            case CONFIRM_RESULT -> confirm(command, teacher, requestHash);
        };
    }

    private VoteCommandResult create(
        VoteCommand command,
        UserAccount teacher,
        String requestHash
    ) {
        validateCreate(command);
        if (
            voteRepository.existsById(command.voteId())
                || activityRepository.existsById(command.voteId())
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "RESOURCE_ALREADY_EXISTS",
                "The vote id is already in use."
            );
        }
        List<ClassroomStudent> students = studentRepository.findAllForUpdate(
            command.classroomId(),
            command.participantIds(),
            ClassroomStudentStatus.ACTIVE
        );
        if (students.size() != command.participantIds().size()) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "NOT_PARTICIPANT",
                "Every participant must be active in this classroom."
            );
        }
        long availableFund = currentFund(command.classroomId())
            .map(LedgerAccount::getCurrentBalance)
            .orElse(0L);
        if (
            command.options().stream()
                .map(VoteOptionCommand::budgetAmount)
                .anyMatch(amount -> amount == null || amount < 0 || amount > availableFund)
        ) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "BUDGET_EXCEEDS_FUND",
                "Each budget option must fit within the current public fund."
            );
        }

        Instant now = clock.instant();
        activityRepository.saveAndFlush(
            ActivitySession.ready(
                command.voteId(),
                command.classroomId(),
                "VOTE",
                teacher.getId(),
                Boolean.TRUE.equals(command.publicDisplayEnabled()),
                now
            )
        );
        Vote vote = voteRepository.saveAndFlush(
            Vote.ready(
                command.voteId(),
                command.classroomId(),
                command.title(),
                command.description(),
                now
            )
        );
        optionRepository.saveAll(options(command, now));
        eligibilityRepository.saveAll(eligibilities(command, students, now));
        VoteCommandResult result = result(vote, null, null);
        remember(command, teacher.getId(), requestHash, result, now);
        audit(
            command,
            teacher.getId(),
            "PUBLIC_FUND_VOTE_CREATED",
            Map.of(),
            Map.of(
                "status", result.status(),
                "option_count", command.options().size(),
                "participant_count", students.size()
            )
        );
        return result;
    }

    private VoteCommandResult open(
        VoteCommand command,
        UserAccount teacher,
        String requestHash
    ) {
        Vote vote = lockedVote(command);
        ActivitySession activity = lockedActivity(command);
        requireExpectedVersion(command.expectedStateVersion(), vote);
        if (activityRepository.existsByClassroomIdAndStatus(
            command.classroomId(),
            "LIVE"
        )) {
            throw operation(
                HttpStatus.CONFLICT,
                "ACTIVITY_ALREADY_LIVE",
                "Only one guided activity can be live in a classroom."
            );
        }
        List<VoteEligibility> eligibilities = eligibilityRepository
            .findAllByClassroomIdAndVoteId(command.classroomId(), command.voteId());
        if (eligibilities.isEmpty()) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "NOT_PARTICIPANT",
                "The vote has no eligible participants."
            );
        }
        Instant now = clock.instant();
        try {
            vote.open(now);
            activity.start(now, PUBLIC_VOTE_FIELDS);
        } catch (IllegalStateException exception) {
            throw invalidTransition(exception);
        }
        voteRepository.saveAndFlush(vote);
        activityRepository.saveAndFlush(activity);
        participantRepository.saveAll(
            eligibilities.stream()
                .map(eligibility -> ActivityParticipant.joined(
                    uuidGenerator.generate(),
                    command.classroomId(),
                    command.voteId(),
                    eligibility.getClassroomStudentId(),
                    now
                ))
                .toList()
        );
        List<VoteOption> options = optionRepository
            .findAllByClassroomIdAndVoteIdOrderBySortOrder(
                command.classroomId(),
                command.voteId()
            );
        VoteCommandResult result = result(vote, null, null);
        remember(command, teacher.getId(), requestHash, result, now);
        eventPublisher.publish(
            command.classroomId(),
            "VOTE",
            command.voteId(),
            "vote.started",
            "VOTE",
            command.voteId(),
            vote.getStateVersion(),
            Map.of(
                "vote_id", vote.getId().toString(),
                "question", vote.getTitle(),
                "options", publicOptions(options),
                "multiple_choice", false
            ),
            true
        );
        audit(
            command,
            teacher.getId(),
            "PUBLIC_FUND_VOTE_OPENED",
            Map.of("status", VoteStatus.READY.name()),
            Map.of("status", result.status())
        );
        return result;
    }

    private VoteCommandResult close(
        VoteCommand command,
        UserAccount teacher,
        String requestHash
    ) {
        Vote vote = lockedVote(command);
        ActivitySession activity = lockedActivity(command);
        requireExpectedVersion(command.expectedStateVersion(), vote);
        Instant now = clock.instant();
        try {
            vote.close(now);
            activity.beginFinalizing(now);
        } catch (IllegalStateException exception) {
            throw invalidTransition(exception);
        }
        List<ActivityParticipant> participants = participantRepository
            .findAllByClassroomIdAndActivitySessionId(
                command.classroomId(),
                command.voteId()
            );
        participants.forEach(participant -> participant.complete(now));
        participantRepository.saveAll(participants);
        voteRepository.saveAndFlush(vote);
        activityRepository.saveAndFlush(activity);
        long submitted = ballotRepository.countByClassroomIdAndVoteId(
            command.classroomId(),
            command.voteId()
        );
        VoteCommandResult result = result(vote, null, null);
        remember(command, teacher.getId(), requestHash, result, now);
        eventPublisher.publish(
            command.classroomId(),
            "VOTE",
            command.voteId(),
            "vote.closed",
            "VOTE",
            command.voteId(),
            vote.getStateVersion(),
            Map.of(
                "closed_at", now.toString(),
                "submitted_count", submitted,
                "total_count", participants.size()
            ),
            true
        );
        displaySessionService.revokeAllForActivity(command.voteId());
        audit(
            command,
            teacher.getId(),
            "PUBLIC_FUND_VOTE_CLOSED",
            Map.of("status", VoteStatus.OPEN.name()),
            Map.of("status", result.status(), "submitted_count", submitted)
        );
        return result;
    }

    private VoteCommandResult confirm(
        VoteCommand command,
        UserAccount teacher,
        String requestHash
    ) {
        Vote vote = lockedVote(command);
        ActivitySession activity = lockedActivity(command);
        requireExpectedVersion(command.expectedStateVersion(), vote);
        if (vote.getStatus() != VoteStatus.CLOSED) {
            throw invalidTransition(
                new IllegalStateException("only a closed vote can publish a result")
            );
        }
        List<VoteOption> options = optionRepository
            .findAllByClassroomIdAndVoteIdOrderBySortOrder(
                command.classroomId(),
                command.voteId()
            );
        List<VoteBallot> ballots = ballotRepository.findAllByClassroomIdAndVoteId(
            command.classroomId(),
            command.voteId()
        );
        if (ballots.isEmpty()) {
            throw operation(
                HttpStatus.CONFLICT,
                "VOTE_COUNT_MISMATCH",
                "A result cannot be confirmed without a ballot."
            );
        }
        Map<UUID, Long> counts = counts(ballots);
        VoteOption winner = winner(options, counts);
        String selectedUse = selectedUse(command.selectedUse(), winner);
        Instant now = clock.instant();
        UUID transactionGroupId = null;

        if (winner.getBudgetAmount() > 0) {
            LedgerAccount fund = currentFund(command.classroomId())
                .orElseThrow(() -> operation(
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_BALANCE",
                    "The classroom public fund has no available balance."
                ));
            LedgerAccount spending = spendingAccount(
                command.classroomId(),
                command.voteId(),
                now
            );
            transactionGroupId = uuidGenerator.generate();
            VotePublishMutation mutation = new VotePublishMutation(
                command,
                vote,
                activity,
                winner,
                selectedUse,
                fund,
                transactionGroupId,
                now
            );
            LedgerTransactionResult ledgerResult = ledgerTransactionService.execute(
                transactionGroupId,
                LedgerTransactionCommand.original(
                    command.classroomId(),
                    "PUBLIC_FUND_SPENDING",
                    LedgerActorType.TEACHER,
                    teacher.getId(),
                    null,
                    ROUTE,
                    command.requestId().toString(),
                    requestHash,
                    command.correlationId().toString(),
                    selectedUse,
                    List.of(
                        new LedgerTransactionPosting(
                            fund.getId(),
                            -winner.getBudgetAmount()
                        ),
                        new LedgerTransactionPosting(
                            spending.getId(),
                            winner.getBudgetAmount()
                        )
                    ),
                    now
                ),
                List.of(mutation)
            );
            transactionGroupId = ledgerResult.transactionGroupId();
        } else {
            publishWithoutSpending(vote, activity, now);
        }

        VoteCommandResult result = result(
            vote,
            winner.getId(),
            transactionGroupId
        );
        if (transactionGroupId == null) {
            remember(command, teacher.getId(), requestHash, result, now);
        }
        eventPublisher.publish(
            command.classroomId(),
            "VOTE",
            command.voteId(),
            "vote.result.published",
            "VOTE",
            command.voteId(),
            vote.getStateVersion(),
            Map.of(
                "option_counts", publicCounts(options, counts),
                "selected_option_id", winner.getId().toString(),
                "fund_use_summary", Map.of(
                    "label", selectedUse,
                    "amount", winner.getBudgetAmount()
                )
            ),
            true
        );
        audit(
            command,
            teacher.getId(),
            "PUBLIC_FUND_RESULT_CONFIRMED",
            Map.of("status", VoteStatus.CLOSED.name()),
            Map.of(
                "status", result.status(),
                "selected_option_id", winner.getId(),
                "selected_use", selectedUse,
                "amount", winner.getBudgetAmount(),
                "transaction_group_id",
                transactionGroupId == null ? "" : transactionGroupId
            )
        );
        return result;
    }

    private void publishWithoutSpending(
        Vote vote,
        ActivitySession activity,
        Instant now
    ) {
        try {
            vote.publishResult(now);
            activity.complete(now);
        } catch (IllegalStateException exception) {
            throw invalidTransition(exception);
        }
        voteRepository.saveAndFlush(vote);
        activityRepository.saveAndFlush(activity);
    }

    private Vote lockedVote(VoteCommand command) {
        return voteRepository
            .findForUpdateByClassroomIdAndId(
                command.classroomId(),
                command.voteId()
            )
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The vote could not be found."
            ));
    }

    private ActivitySession lockedActivity(VoteCommand command) {
        return activityRepository
            .findByClassroomIdAndId(command.classroomId(), command.voteId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The vote activity could not be found."
            ));
    }

    private List<VoteOption> options(VoteCommand command, Instant now) {
        List<VoteOption> result = new ArrayList<>();
        for (int index = 0; index < command.options().size(); index++) {
            VoteOptionCommand option = command.options().get(index);
            result.add(VoteOption.create(
                uuidGenerator.generate(),
                command.classroomId(),
                command.voteId(),
                option.label(),
                option.description(),
                index,
                option.budgetAmount(),
                now
            ));
        }
        return result;
    }

    private List<VoteEligibility> eligibilities(
        VoteCommand command,
        List<ClassroomStudent> students,
        Instant now
    ) {
        return students.stream()
            .map(student -> VoteEligibility.create(
                uuidGenerator.generate(),
                command.classroomId(),
                command.voteId(),
                student.getId(),
                sha256(
                    command.voteId() + "|" + student.getId() + "|"
                        + uuidGenerator.generate()
                ),
                now
            ))
            .toList();
    }

    private Optional<LedgerAccount> currentFund(UUID classroomId) {
        return ledgerAccountRepository
            .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                classroomId,
                CLASSROOM_FUND_KEY,
                LedgerAccountType.CLASSROOM_FUND,
                LedgerAccountStatus.ACTIVE
            );
    }

    private LedgerAccount spendingAccount(
        UUID classroomId,
        UUID voteId,
        Instant now
    ) {
        String key = "PUBLIC_SPENDING:" + voteId;
        return ledgerAccountRepository
            .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                classroomId,
                key,
                LedgerAccountType.PUBLIC_SPENDING,
                LedgerAccountStatus.ACTIVE
            )
            .orElseGet(() -> ledgerAccountRepository.save(
                LedgerAccount.systemAccount(
                    uuidGenerator.generate(),
                    classroomId,
                    key,
                    LedgerAccountType.PUBLIC_SPENDING,
                    now
                )
            ));
    }

    private VoteCommandResult replay(
        VoteCommand command,
        UUID teacherId,
        String requestHash
    ) {
        IdempotencyRecord record = idempotencyRepository
            .findForReplay(
                command.classroomId(),
                teacherId,
                ROUTE,
                command.requestId().toString()
            )
            .orElse(null);
        if (record == null) {
            return null;
        }
        if (!record.hasRequestHash(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        Map<String, Object> body = record.getResponseBody();
        Vote vote = voteRepository
            .findByClassroomIdAndId(command.classroomId(), command.voteId())
            .orElse(null);
        if (body.containsKey("vote_id")) {
            return new VoteCommandResult(
                UUID.fromString(body.get("vote_id").toString()),
                body.get("status").toString(),
                ((Number) body.get("state_version")).longValue(),
                uuid(body.get("selected_option_id")),
                uuid(body.get("transaction_group_id"))
            );
        }
        if (vote != null && record.getTransactionGroupId() != null) {
            VoteOption winner = winner(
                optionRepository.findAllByClassroomIdAndVoteIdOrderBySortOrder(
                    command.classroomId(),
                    command.voteId()
                ),
                counts(ballotRepository.findAllByClassroomIdAndVoteId(
                    command.classroomId(),
                    command.voteId()
                ))
            );
            return result(vote, winner.getId(), record.getTransactionGroupId());
        }
        throw operation(
            HttpStatus.CONFLICT,
            "IDEMPOTENCY_RESULT_MISSING",
            "The previous vote command result could not be found."
        );
    }

    private void remember(
        VoteCommand command,
        UUID teacherId,
        String requestHash,
        VoteCommandResult result,
        Instant now
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vote_id", result.voteId().toString());
        body.put("status", result.status());
        body.put("state_version", result.stateVersion());
        if (result.selectedOptionId() != null) {
            body.put("selected_option_id", result.selectedOptionId().toString());
        }
        if (result.transactionGroupId() != null) {
            body.put("transaction_group_id", result.transactionGroupId().toString());
        }
        idempotencyRepository.save(
            IdempotencyRecord.completed(
                uuidGenerator.generate(),
                command.classroomId(),
                teacherId,
                ROUTE,
                command.requestId().toString(),
                requestHash,
                HttpStatus.OK.value(),
                body,
                result.transactionGroupId(),
                now.plus(IDEMPOTENCY_TTL),
                now
            )
        );
    }

    private void audit(
        VoteCommand command,
        UUID teacherId,
        String action,
        Map<String, Object> before,
        Map<String, Object> after
    ) {
        auditTrailService.record(
            command.classroomId(),
            teacherId,
            action,
            "VOTE",
            command.voteId(),
            command.requestId(),
            command.correlationId(),
            before,
            after,
            "PUBLIC_FUND",
            "공공기금 투표 상태가 변경되었습니다."
        );
    }

    private void requireActiveClassroom(UUID classroomId) {
        Classroom classroom = classroomRepository.findById(classroomId)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The classroom could not be found."
            ));
        if (classroom.getStatus() != ClassroomStatus.ACTIVE) {
            throw operation(
                HttpStatus.CONFLICT,
                "CLASS_NOT_ACTIVE",
                "Public fund votes can change only in an active classroom."
            );
        }
    }

    private static void validateEnvelope(VoteCommand command) {
        if (
            command == null
                || command.classroomId() == null
                || command.voteId() == null
                || command.requestId() == null
                || command.correlationId() == null
                || command.command() == null
        ) {
            throw validation("Check the vote command values.");
        }
    }

    private static void validateCreate(VoteCommand command) {
        if (
            command.title() == null
                || command.title().isBlank()
                || command.title().length() > 160
                || command.description() != null
                    && command.description().length() > 2_000
                || command.options() == null
                || command.options().size() < 2
                || command.options().size() > 10
                || command.participantIds() == null
                || command.participantIds().isEmpty()
                || new HashSet<>(command.participantIds()).size()
                    != command.participantIds().size()
        ) {
            throw validation("Check the public fund vote proposal values.");
        }
        Set<String> labels = new HashSet<>();
        for (VoteOptionCommand option : command.options()) {
            if (
                option == null
                    || option.label() == null
                    || option.label().isBlank()
                    || option.label().length() > 160
                    || option.description() != null
                        && option.description().length() > 2_000
                    || option.budgetAmount() == null
                    || option.budgetAmount() < 0
                    || !labels.add(option.label().trim().toLowerCase())
            ) {
                throw validation("Vote option labels and budgets must be valid.");
            }
        }
    }

    private static void requireExpectedVersion(Long expected, Vote vote) {
        if (expected != null && expected != vote.getStateVersion()) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The vote state has changed."
            );
        }
    }

    private static Map<UUID, Long> counts(List<VoteBallot> ballots) {
        Map<UUID, Long> counts = new LinkedHashMap<>();
        ballots.forEach(ballot -> counts.merge(
            ballot.getVoteOptionId(),
            1L,
            Long::sum
        ));
        return counts;
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
                if (byCount != 0) {
                    return byCount;
                }
                return Integer.compare(right.getSortOrder(), left.getSortOrder());
            })
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "VOTE_COUNT_MISMATCH",
                "The vote has no options."
            ));
    }

    private static String selectedUse(String requested, VoteOption winner) {
        if (requested == null || requested.isBlank()) {
            return winner.getLabel();
        }
        String normalized = requested.trim();
        if (normalized.length() > 500) {
            throw validation("selectedUse must not exceed 500 characters.");
        }
        return normalized;
    }

    private static List<Map<String, Object>> publicOptions(
        List<VoteOption> options
    ) {
        return options.stream()
            .map(option -> Map.<String, Object>of(
                "option_id", option.getId().toString(),
                "label", option.getLabel(),
                "budget_amount", option.getBudgetAmount()
            ))
            .toList();
    }

    private static List<Map<String, Object>> publicCounts(
        List<VoteOption> options,
        Map<UUID, Long> counts
    ) {
        return options.stream()
            .map(option -> Map.<String, Object>of(
                "option_id", option.getId().toString(),
                "count", counts.getOrDefault(option.getId(), 0L)
            ))
            .toList();
    }

    private static VoteCommandResult result(
        Vote vote,
        UUID selectedOptionId,
        UUID transactionGroupId
    ) {
        return new VoteCommandResult(
            vote.getId(),
            vote.getStatus().name(),
            vote.getStateVersion(),
            selectedOptionId,
            transactionGroupId
        );
    }

    private static UUID uuid(Object value) {
        return value == null || value.toString().isBlank()
            ? null
            : UUID.fromString(value.toString());
    }

    private static String hash(VoteCommand command) {
        return sha256(
            command.classroomId() + "|" + command.voteId() + "|"
                + command.command() + "|" + command.title() + "|"
                + command.description() + "|" + command.options() + "|"
                + command.participantIds() + "|" + command.publicDisplayEnabled()
                + "|" + command.selectedUse() + "|"
                + command.expectedStateVersion()
        );
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash the vote command.", exception);
        }
    }

    private static ClassroomOperationException validation(String message) {
        return operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private static ClassroomOperationException invalidTransition(
        IllegalStateException exception
    ) {
        return operation(
            HttpStatus.CONFLICT,
            "INVALID_STATE_TRANSITION",
            exception.getMessage()
        );
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }

    private final class VotePublishMutation implements LedgerDomainMutation {

        private final VoteCommand command;
        private final Vote vote;
        private final ActivitySession activity;
        private final VoteOption winner;
        private final String selectedUse;
        private final LedgerAccount fund;
        private final UUID transactionGroupId;
        private final Instant now;

        private VotePublishMutation(
            VoteCommand command,
            Vote vote,
            ActivitySession activity,
            VoteOption winner,
            String selectedUse,
            LedgerAccount fund,
            UUID transactionGroupId,
            Instant now
        ) {
            this.command = command;
            this.vote = vote;
            this.activity = activity;
            this.winner = winner;
            this.selectedUse = selectedUse;
            this.fund = fund;
            this.transactionGroupId = transactionGroupId;
            this.now = now;
        }

        @Override
        public void validate() {
            if (
                vote.getStatus() != VoteStatus.CLOSED
                    || !"FINALIZING".equals(activity.getStatus())
            ) {
                throw operation(
                    HttpStatus.CONFLICT,
                    "INVALID_STATE_TRANSITION",
                    "The vote is no longer ready to publish."
                );
            }
            if (fund.getCurrentBalance() < winner.getBudgetAmount()) {
                throw operation(
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_BALANCE",
                    "The public fund balance is insufficient."
                );
            }
        }

        @Override
        public void apply() {
            vote.publishResult(now);
            activity.complete(now);
            voteRepository.saveAndFlush(vote);
            activityRepository.save(activity);
            businessRefRepository.save(
                TransactionBusinessRef.create(
                    uuidGenerator.generate(),
                    command.classroomId(),
                    transactionGroupId,
                    "PUBLIC_FUND_VOTE",
                    command.voteId(),
                    now
                )
            );
        }
    }
}
