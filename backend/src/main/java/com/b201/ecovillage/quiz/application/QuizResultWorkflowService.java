package com.b201.ecovillage.quiz.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.application.AtomicLedgerTransactionService;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.application.LedgerDomainMutation;
import com.b201.ecovillage.ledger.application.LedgerRecoveryCommand;
import com.b201.ecovillage.ledger.application.LedgerRecoveryService;
import com.b201.ecovillage.ledger.application.LedgerRecoveryType;
import com.b201.ecovillage.ledger.application.LedgerTransactionCommand;
import com.b201.ecovillage.ledger.application.LedgerTransactionPosting;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.LedgerActorType;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.quiz.domain.ActivitySession;
import com.b201.ecovillage.quiz.domain.QuizMode;
import com.b201.ecovillage.quiz.domain.QuizQuestionIssue;
import com.b201.ecovillage.quiz.domain.QuizResultVersion;
import com.b201.ecovillage.quiz.domain.QuizSession;
import com.b201.ecovillage.quiz.domain.QuizSessionQuestion;
import com.b201.ecovillage.quiz.domain.QuizSessionStatus;
import com.b201.ecovillage.quiz.domain.QuizStudentResult;
import com.b201.ecovillage.quiz.domain.RegradingRun;
import com.b201.ecovillage.quiz.repository.ActivityParticipantRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;
import com.b201.ecovillage.quiz.repository.QuizQuestionIssueRepository;
import com.b201.ecovillage.quiz.repository.QuizResultVersionRepository;
import com.b201.ecovillage.quiz.repository.QuizSessionQuestionRepository;
import com.b201.ecovillage.quiz.repository.QuizSessionRepository;
import com.b201.ecovillage.quiz.repository.QuizStudentResultRepository;
import com.b201.ecovillage.quiz.repository.RegradingRunRepository;

@Service
public class QuizResultWorkflowService {

    private static final String RESULT_ROUTE =
        "/classrooms/{classroom_id}/quiz-sessions/{resource_id}/result-commands";
    private static final String REPORT_ROUTE =
        "/classrooms/{classroom_id}/quiz-sessions/{resource_id}/question-issue-reports";
    private static final String REGRADE_ROUTE =
        "/classrooms/{classroom_id}/quiz-sessions/{resource_id}/regrade-operations";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);
    private static final String MONEY_ISSUANCE_KEY = "MONEY_ISSUANCE";

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ActivitySessionRepository activityRepository;
    private final ActivityParticipantRepository participantRepository;
    private final QuizSessionRepository sessionRepository;
    private final QuizSessionQuestionRepository sessionQuestionRepository;
    private final QuizResultVersionRepository resultRepository;
    private final QuizStudentResultRepository studentResultRepository;
    private final QuizQuestionIssueRepository issueRepository;
    private final RegradingRunRepository regradingRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final AtomicLedgerTransactionService transactionService;
    private final LedgerRecoveryService recoveryService;
    private final QuizScoringService scoringService;
    private final QuizRewardBudgetService rewardBudgetService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public QuizResultWorkflowService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ActivitySessionRepository activityRepository,
        ActivityParticipantRepository participantRepository,
        QuizSessionRepository sessionRepository,
        QuizSessionQuestionRepository sessionQuestionRepository,
        QuizResultVersionRepository resultRepository,
        QuizStudentResultRepository studentResultRepository,
        QuizQuestionIssueRepository issueRepository,
        RegradingRunRepository regradingRepository,
        LedgerAccountRepository ledgerAccountRepository,
        IdempotencyRecordRepository idempotencyRepository,
        AtomicLedgerTransactionService transactionService,
        LedgerRecoveryService recoveryService,
        QuizScoringService scoringService,
        QuizRewardBudgetService rewardBudgetService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.participantRepository = participantRepository;
        this.sessionRepository = sessionRepository;
        this.sessionQuestionRepository = sessionQuestionRepository;
        this.resultRepository = resultRepository;
        this.studentResultRepository = studentResultRepository;
        this.issueRepository = issueRepository;
        this.regradingRepository = regradingRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.transactionService = transactionService;
        this.recoveryService = recoveryService;
        this.scoringService = scoringService;
        this.rewardBudgetService = rewardBudgetService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public QuizResourceResult execute(QuizResultCommand command) {
        validate(command);
        authorizationService.authorize(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.MANAGE_QUIZ_SESSIONS,
            null
        );
        UserAccount teacher = teacher(command.teacherSubject());
        String requestHash = hash(command);
        QuizResourceResult replay = replayResult(
            command.classroomId(),
            teacher.getId(),
            command.requestId(),
            RESULT_ROUTE,
            requestHash
        );
        if (replay != null) {
            return replay;
        }
        QuizSession session = lockedSession(command.classroomId(), command.sessionId());
        replay = replayResult(
            command.classroomId(),
            teacher.getId(),
            command.requestId(),
            RESULT_ROUTE,
            requestHash
        );
        if (replay != null) {
            return replay;
        }
        verifyStateVersion(session, command.expectedStateVersion());

        Instant now = clock.instant();
        CommandOutcome outcome = switch (command.command()) {
            case CONFIRM -> confirm(command, session, now);
            case PUBLISH -> publish(command, session, teacher, requestHash, now);
            case CLOSE -> close(command, session, now);
        };
        sessionRepository.flush();
        resultRepository.flush();
        QuizResourceResult result = new QuizResourceResult(
            outcome.resultVersion().getId(),
            session.getStatus().name(),
            session.getStateVersion()
        );
        remember(
            command.classroomId(),
            teacher.getId(),
            command.requestId(),
            RESULT_ROUTE,
            requestHash,
            HttpStatus.OK,
            Map.of(
                "quiz_resource_id", result.quizResourceId(),
                "status", result.status(),
                "state_version", result.stateVersion()
            ),
            outcome.transactionGroupId(),
            now
        );
        return result;
    }

    @Transactional
    public QuizQuestionIssueResult report(QuizQuestionIssueReportCommand command) {
        validate(command);
        UUID studentId = studentId(command.studentSubject());
        authorizationService.authorize(
            command.studentSubject(),
            command.classroomId(),
            ClassroomPermission.REPORT_QUIZ_QUESTION,
            studentId
        );
        String requestHash = hash(command);
        QuizQuestionIssueResult replay = replayIssue(command, studentId, requestHash);
        if (replay != null) {
            return replay;
        }
        QuizSession session = lockedSession(command.classroomId(), command.sessionId());
        replay = replayIssue(command, studentId, requestHash);
        if (replay != null) {
            return replay;
        }
        verifyStateVersion(session, command.expectedStateVersion());
        if (
            session.getStatus() != QuizSessionStatus.RESULT_PUBLISHED
                && session.getStatus() != QuizSessionStatus.CLOSED
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "INVALID_STATE_TRANSITION",
                "A question can be reported after quiz results are published."
            );
        }
        if (!participantRepository
            .existsByClassroomIdAndActivitySessionIdAndClassroomStudentId(
                command.classroomId(),
                command.sessionId(),
                studentId
            )) {
            throw operation(
                HttpStatus.FORBIDDEN,
                "NOT_EXAMINEE",
                "Only an examinee in this quiz can report a question."
            );
        }
        QuizSessionQuestion question = sessionQuestionRepository
            .findByClassroomIdAndQuizSessionIdAndId(
                command.classroomId(),
                command.sessionId(),
                command.questionId()
            )
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The quiz session question could not be found."
            ));
        if (issueRepository
            .existsByClassroomIdAndQuizSessionQuestionIdAndReportedByStudentId(
                command.classroomId(),
                question.getId(),
                studentId
            )) {
            throw operation(
                HttpStatus.CONFLICT,
                "DUPLICATE_REPORT",
                "The student already reported this question."
            );
        }
        Instant now = clock.instant();
        QuizQuestionIssue issue = issueRepository.saveAndFlush(
            QuizQuestionIssue.report(
                uuidGenerator.generate(),
                command.classroomId(),
                question.getId(),
                studentId,
                command.reasonCode(),
                command.description(),
                now
            )
        );
        QuizQuestionIssueResult result = new QuizQuestionIssueResult(
            issue.getId(),
            issue.getStatus(),
            issue.getStateVersion()
        );
        remember(
            command.classroomId(),
            studentId,
            command.requestId(),
            REPORT_ROUTE,
            requestHash,
            HttpStatus.CREATED,
            Map.of(
                "issue_id", result.issueId(),
                "status", result.status(),
                "state_version", result.stateVersion()
            ),
            null,
            now
        );
        return result;
    }

    @Transactional
    public QuizRegradeResult regrade(QuizRegradeCommand command) {
        validate(command);
        authorizationService.authorize(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.MANAGE_QUIZ_SESSIONS,
            null
        );
        authorizationService.authorize(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.REVIEW_QUIZ_QUESTION,
            null
        );
        UserAccount teacher = teacher(command.teacherSubject());
        String requestHash = hash(command);
        QuizRegradeResult replay = replayRegrade(command, teacher.getId(), requestHash);
        if (replay != null) {
            return replay;
        }
        QuizSession session = lockedSession(command.classroomId(), command.sessionId());
        replay = replayRegrade(command, teacher.getId(), requestHash);
        if (replay != null) {
            return replay;
        }
        verifyStateVersion(session, command.expectedStateVersion());
        if (
            session.getStatus() != QuizSessionStatus.REVIEWING
                && session.getStatus() != QuizSessionStatus.RESULT_PUBLISHED
                && session.getStatus() != QuizSessionStatus.CLOSED
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "INVALID_STATE_TRANSITION",
                "The quiz is not in a regradable result state."
            );
        }
        QuizResultVersion source = latestResult(command.classroomId(), command.sessionId());
        if (source.getVersion() != command.expectedResultVersion()) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The quiz result version has changed."
            );
        }
        if (regradingRepository.existsByClassroomIdAndQuizSessionIdAndStatus(
            command.classroomId(),
            command.sessionId(),
            "RUNNING"
        )) {
            throw operation(
                HttpStatus.CONFLICT,
                "REGRADING_ALREADY_RUNNING",
                "A regrading operation is already running for this quiz."
            );
        }
        List<QuizSessionQuestion> questions = sessionQuestionRepository
            .findAllByClassroomIdAndQuizSessionIdOrderBySequenceNumber(
                command.classroomId(),
                command.sessionId()
            );
        Set<UUID> questionIds = questions.stream()
            .map(QuizSessionQuestion::getId)
            .collect(java.util.stream.Collectors.toSet());
        if (!questionIds.containsAll(command.invalidatedQuestionIds())) {
            throw operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "One or more invalidated questions do not belong to this quiz."
            );
        }
        List<QuizQuestionIssue> issues = issueRepository
            .findAllByClassroomIdAndQuizSessionQuestionIdInAndStatus(
                command.classroomId(),
                command.invalidatedQuestionIds(),
                "UNDER_REVIEW"
            );
        Set<UUID> reviewedQuestionIds = issues.stream()
            .map(QuizQuestionIssue::getQuizSessionQuestionId)
            .collect(java.util.stream.Collectors.toSet());
        if (!reviewedQuestionIds.containsAll(command.invalidatedQuestionIds())) {
            throw operation(
                HttpStatus.CONFLICT,
                "QUESTION_NOT_UNDER_REVIEW",
                "Every invalidated question must have an issue under review."
            );
        }

        Instant now = clock.instant();
        RegradingRun run = regradingRepository.saveAndFlush(RegradingRun.start(
            uuidGenerator.generate(),
            command.classroomId(),
            command.sessionId(),
            source.getId(),
            command.reason(),
            teacher.getId(),
            now
        ));
        issues.forEach(issue -> issue.invalidate(command.reason(), teacher.getId(), now));
        issueRepository.flush();
        Set<UUID> allInvalidated = new HashSet<>(command.invalidatedQuestionIds());
        allInvalidated.addAll(issueRepository
            .findAllByClassroomIdAndQuizSessionQuestionIdInAndStatus(
                command.classroomId(),
                questionIds,
                "INVALIDATED"
            )
            .stream()
            .map(QuizQuestionIssue::getQuizSessionQuestionId)
            .toList());

        boolean published = "PUBLISHED".equals(source.getStatus());
        QuizResultVersion next = resultRepository.save(QuizResultVersion.superseding(
            uuidGenerator.generate(),
            command.classroomId(),
            command.sessionId(),
            source.getVersion() + 1,
            source.getId(),
            published,
            now
        ));
        long rewardPerCorrectAnswer = rewardPerCorrectAnswer(session);
        List<QuizStudentResult> nextResults = scoringService.score(
            command.classroomId(),
            command.sessionId(),
            next.getId(),
            session.getMode(),
            rewardPerCorrectAnswer,
            allInvalidated,
            now
        );
        studentResultRepository.saveAll(nextResults);

        UUID newRewardGroupId = null;
        if (published && source.getRewardTransactionGroupId() != null) {
            recoveryService.recover(new LedgerRecoveryCommand(
                command.classroomId(),
                source.getRewardTransactionGroupId(),
                LedgerRecoveryType.QUIZ_REGRADING,
                LedgerActorType.TEACHER,
                teacher.getId(),
                REGRADE_ROUTE + "/reward-recoveries",
                command.requestId() + "-recovery",
                sha256(requestHash + "|recovery"),
                command.correlationId().toString(),
                command.reason(),
                now
            ));
            long totalReward = sumRewards(nextResults);
            if (totalReward > 0L) {
                newRewardGroupId = uuidGenerator.generate();
                UUID attachedGroupId = newRewardGroupId;
                transactionService.execute(
                    newRewardGroupId,
                    rewardTransaction(
                        command.classroomId(),
                        teacher.getId(),
                        command.requestId() + "-reissue",
                        command.correlationId(),
                        requestHash,
                        nextResults,
                        command.reason(),
                        now,
                        REGRADE_ROUTE + "/reward-reissues"
                    ),
                    List.of(mutation(() -> next.attachRewardTransaction(attachedGroupId)))
                );
            }
        }
        run.complete(next.getId(), now);
        resultRepository.flush();
        regradingRepository.flush();

        QuizRegradeResult result = new QuizRegradeResult(run.getId(), "RUNNING");
        remember(
            command.classroomId(),
            teacher.getId(),
            command.requestId(),
            REGRADE_ROUTE,
            requestHash,
            HttpStatus.ACCEPTED,
            Map.of(
                "operation_id", result.operationId(),
                "status", result.status()
            ),
            newRewardGroupId,
            now
        );
        return result;
    }

    private CommandOutcome confirm(
        QuizResultCommand command,
        QuizSession session,
        Instant now
    ) {
        if (session.getStatus() != QuizSessionStatus.SCORING) {
            throw invalidTransition("Only a scoring quiz can confirm its calculated result.");
        }
        if (resultRepository
            .findTopByClassroomIdAndQuizSessionIdOrderByVersionDesc(
                command.classroomId(),
                command.sessionId()
            )
            .isPresent()) {
            throw invalidTransition("The quiz already has a result version.");
        }
        List<QuizSessionQuestion> questions = sessionQuestionRepository
            .findAllByClassroomIdAndQuizSessionIdOrderBySequenceNumber(
                command.classroomId(),
                command.sessionId()
            );
        if (questions.isEmpty()) {
            throw operation(
                HttpStatus.CONFLICT,
                "SCORING_NOT_FINISHED",
                "The quiz has no scored questions."
            );
        }
        long reward = rewardPerCorrectAnswer(session);
        QuizResultVersion result = resultRepository.save(QuizResultVersion.reviewing(
            uuidGenerator.generate(),
            command.classroomId(),
            command.sessionId(),
            now
        ));
        studentResultRepository.saveAll(scoringService.score(
            command.classroomId(),
            command.sessionId(),
            result.getId(),
            session.getMode(),
            reward,
            Set.of(),
            now
        ));
        try {
            session.beginResultReview(now);
        } catch (IllegalStateException exception) {
            throw invalidTransition(exception.getMessage());
        }
        return new CommandOutcome(result, null);
    }

    private CommandOutcome publish(
        QuizResultCommand command,
        QuizSession session,
        UserAccount teacher,
        String requestHash,
        Instant now
    ) {
        if (session.getStatus() != QuizSessionStatus.REVIEWING) {
            throw invalidTransition("Only a reviewed quiz result can be published.");
        }
        QuizResultVersion result = latestResult(command.classroomId(), command.sessionId());
        verifyResultVersion(result, command.expectedResultVersion());
        boolean leaderboardPublic = Boolean.TRUE.equals(command.leaderboardPublic());
        if (session.getMode() == QuizMode.TEST && leaderboardPublic) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "PUBLIC_DATA_ONLY",
                "A TEST quiz cannot publish a leaderboard."
            );
        }
        List<QuizStudentResult> results = studentResultRepository
            .findAllByQuizResultVersionIdOrderByRankAsc(result.getId());
        long totalReward = sumRewards(results);
        UUID transactionGroupId = null;
        if (totalReward > 0L) {
            QuizRewardBudget budget = rewardBudgetService.evaluate(
                command.classroomId(),
                totalReward
            );
            if (budget.blocked() && !Boolean.TRUE.equals(command.rewardWarningAcknowledged())) {
                throw operation(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "REWARD_ISSUANCE_SHARE_EXCEEDED",
                    "퀴즈 보상을 지급하면 전체 발행량 중 보상 비중이 30%를 넘습니다. 교사가 확인한 뒤 공개할 수 있습니다."
                );
            }
            transactionGroupId = uuidGenerator.generate();
            UUID publishedGroupId = transactionGroupId;
            transactionService.execute(
                transactionGroupId,
                rewardTransaction(
                    command.classroomId(),
                    teacher.getId(),
                    command.requestId() + "-reward",
                    command.correlationId(),
                    requestHash,
                    results,
                    "퀴즈 보상",
                    now,
                    RESULT_ROUTE + "/reward-transactions"
                ),
                List.of(mutation(() -> {
                    session.publishResults(leaderboardPublic, now);
                    result.publish(publishedGroupId, now);
                }))
            );
        } else {
            try {
                session.publishResults(leaderboardPublic, now);
                result.publish(null, now);
            } catch (IllegalStateException exception) {
                throw invalidTransition(exception.getMessage());
            }
        }
        return new CommandOutcome(result, transactionGroupId);
    }

    private CommandOutcome close(
        QuizResultCommand command,
        QuizSession session,
        Instant now
    ) {
        QuizResultVersion result = latestResult(command.classroomId(), command.sessionId());
        verifyResultVersion(result, command.expectedResultVersion());
        ActivitySession activity = activityRepository
            .findByClassroomIdAndId(command.classroomId(), command.sessionId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The quiz activity could not be found."
            ));
        try {
            session.closeResults(now);
            activity.complete(now);
        } catch (IllegalStateException exception) {
            throw invalidTransition(exception.getMessage());
        }
        return new CommandOutcome(result, result.getRewardTransactionGroupId());
    }

    private LedgerTransactionCommand rewardTransaction(
        UUID classroomId,
        UUID teacherId,
        String idempotencyKey,
        UUID correlationId,
        String requestHash,
        List<QuizStudentResult> results,
        String reason,
        Instant now,
        String route
    ) {
        long totalReward = sumRewards(results);
        LedgerAccount issuance = systemAccount(classroomId, now);
        List<LedgerTransactionPosting> postings = new ArrayList<>();
        postings.add(new LedgerTransactionPosting(issuance.getId(), -totalReward));
        results.stream()
            .filter(result -> result.getRewardAmount() > 0L)
            .sorted(Comparator.comparing(QuizStudentResult::getClassroomStudentId))
            .forEach(result -> postings.add(new LedgerTransactionPosting(
                studentWallet(classroomId, result.getClassroomStudentId(), now).getId(),
                result.getRewardAmount()
            )));
        return LedgerTransactionCommand.original(
            classroomId,
            "QUIZ_REWARD",
            LedgerActorType.TEACHER,
            teacherId,
            null,
            route,
            idempotencyKey,
            sha256(requestHash + "|" + results.stream()
                .map(result -> result.getClassroomStudentId() + ":" + result.getRewardAmount())
                .sorted()
                .toList()),
            correlationId.toString(),
            reason,
            postings,
            now
        );
    }

    private LedgerAccount studentWallet(UUID classroomId, UUID studentId, Instant now) {
        return ledgerAccountRepository
            .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                classroomId,
                studentId,
                LedgerAccountType.STUDENT_WALLET,
                LedgerAccountStatus.ACTIVE
            )
            .orElseGet(() -> ledgerAccountRepository.save(LedgerAccount.studentWallet(
                uuidGenerator.generate(),
                classroomId,
                studentId,
                now
            )));
    }

    private LedgerAccount systemAccount(UUID classroomId, Instant now) {
        return ledgerAccountRepository
            .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                classroomId,
                MONEY_ISSUANCE_KEY,
                LedgerAccountType.MONEY_ISSUANCE,
                LedgerAccountStatus.ACTIVE
            )
            .orElseGet(() -> ledgerAccountRepository.save(LedgerAccount.systemAccount(
                uuidGenerator.generate(),
                classroomId,
                MONEY_ISSUANCE_KEY,
                LedgerAccountType.MONEY_ISSUANCE,
                now
            )));
    }

    private QuizSession lockedSession(UUID classroomId, UUID sessionId) {
        return sessionRepository.findForUpdate(classroomId, sessionId)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The quiz session could not be found."
            ));
    }

    private QuizResultVersion latestResult(UUID classroomId, UUID sessionId) {
        return resultRepository.findLatestForUpdate(classroomId, sessionId)
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "SCORING_NOT_FINISHED",
                "The quiz result has not been calculated."
            ));
    }

    private UserAccount teacher(String subject) {
        return userRepository.findByExternalSubject(subject)
            .orElseThrow(() -> operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A valid teacher session is required."
            ));
    }

    /** 교사가 정한 문제당 보상액이다. 학생은 맞힌 문제 수만큼 곱해 받는다. */
    private long rewardPerCorrectAnswer(QuizSession session) {
        Object value = session.getRewardPolicyPublic().get("reward_per_person");
        if (value == null) {
            value = session.getRewardPolicyPublic().get("base_reward");
        }
        long reward;
        try {
            reward = value instanceof Number number
                ? new BigDecimal(number.toString()).longValueExact()
                : value == null ? 0L : new BigDecimal(value.toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_REWARD_POLICY",
                "reward_per_person must be a non-negative integer."
            );
        }
        if (reward < 0L) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_REWARD_POLICY",
                "reward_per_person must be a non-negative integer."
            );
        }
        if (session.getMode() == QuizMode.TEST && reward > 0L) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "FORBIDDEN_REWARD_CONTEXT",
                "A TEST quiz cannot receive a monetary reward."
            );
        }
        return reward;
    }

    private QuizResourceResult replayResult(
        UUID classroomId,
        UUID actorId,
        UUID requestId,
        String route,
        String requestHash
    ) {
        IdempotencyRecord record = replayRecord(
            classroomId,
            actorId,
            requestId,
            route,
            requestHash
        );
        if (record == null) {
            return null;
        }
        Map<String, Object> body = record.getResponseBody();
        return new QuizResourceResult(
            uuid(body.get("quiz_resource_id")),
            String.valueOf(body.get("status")),
            Long.parseLong(String.valueOf(body.get("state_version")))
        );
    }

    private QuizQuestionIssueResult replayIssue(
        QuizQuestionIssueReportCommand command,
        UUID studentId,
        String requestHash
    ) {
        IdempotencyRecord record = replayRecord(
            command.classroomId(),
            studentId,
            command.requestId(),
            REPORT_ROUTE,
            requestHash
        );
        if (record == null) {
            return null;
        }
        Map<String, Object> body = record.getResponseBody();
        return new QuizQuestionIssueResult(
            uuid(body.get("issue_id")),
            String.valueOf(body.get("status")),
            Long.parseLong(String.valueOf(body.get("state_version")))
        );
    }

    private QuizRegradeResult replayRegrade(
        QuizRegradeCommand command,
        UUID teacherId,
        String requestHash
    ) {
        IdempotencyRecord record = replayRecord(
            command.classroomId(),
            teacherId,
            command.requestId(),
            REGRADE_ROUTE,
            requestHash
        );
        if (record == null) {
            return null;
        }
        Map<String, Object> body = record.getResponseBody();
        return new QuizRegradeResult(
            uuid(body.get("operation_id")),
            String.valueOf(body.get("status"))
        );
    }

    private IdempotencyRecord replayRecord(
        UUID classroomId,
        UUID actorId,
        UUID requestId,
        String route,
        String requestHash
    ) {
        IdempotencyRecord record = idempotencyRepository
            .findForReplay(classroomId, actorId, route, requestId.toString())
            .orElse(null);
        if (record != null && !record.hasRequestHash(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        return record;
    }

    private void remember(
        UUID classroomId,
        UUID actorId,
        UUID requestId,
        String route,
        String requestHash,
        HttpStatus responseStatus,
        Map<String, Object> body,
        UUID transactionGroupId,
        Instant now
    ) {
        idempotencyRepository.save(IdempotencyRecord.completed(
            uuidGenerator.generate(),
            classroomId,
            actorId,
            route,
            requestId.toString(),
            requestHash,
            responseStatus.value(),
            body,
            transactionGroupId,
            now.plus(IDEMPOTENCY_TTL),
            now
        ));
    }

    private static void verifyStateVersion(QuizSession session, Long expected) {
        if (expected != null && expected != session.getStateVersion()) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The quiz session state has changed."
            );
        }
    }

    private static void verifyResultVersion(
        QuizResultVersion result,
        Integer expected
    ) {
        if (expected != null && expected != result.getVersion()) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The quiz result version has changed."
            );
        }
    }

    private static long sumRewards(List<QuizStudentResult> results) {
        long total = 0L;
        for (QuizStudentResult result : results) {
            total = Math.addExact(total, result.getRewardAmount());
        }
        return total;
    }

    private static LedgerDomainMutation mutation(Runnable action) {
        return new LedgerDomainMutation() {
            @Override
            public void validate() {
            }

            @Override
            public void apply() {
                action.run();
            }
        };
    }

    private static UUID studentId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (RuntimeException exception) {
            throw operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A valid student session is required."
            );
        }
    }

    private static UUID uuid(Object value) {
        try {
            return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw operation(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_RESULT_MISSING",
                "The previous quiz workflow response is invalid."
            );
        }
    }

    private static String hash(QuizResultCommand command) {
        return sha256(
            command.classroomId() + "|" + command.sessionId() + "|"
                + command.expectedStateVersion() + "|" + command.expectedResultVersion()
                + "|" + command.command() + "|" + command.leaderboardPublic()
                + "|" + command.rewardWarningAcknowledged()
        );
    }

    private static String hash(QuizQuestionIssueReportCommand command) {
        return sha256(
            command.classroomId() + "|" + command.sessionId() + "|"
                + command.questionId() + "|" + command.expectedStateVersion() + "|"
                + command.reasonCode() + "|" + command.description()
        );
    }

    private static String hash(QuizRegradeCommand command) {
        List<UUID> ids = command.invalidatedQuestionIds().stream().sorted().toList();
        return sha256(
            command.classroomId() + "|" + command.sessionId() + "|"
                + command.expectedStateVersion() + "|" + command.expectedResultVersion()
                + "|" + ids + "|" + command.reason()
        );
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                value.getBytes(StandardCharsets.UTF_8)
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash the quiz workflow request.", exception);
        }
    }

    private static void validate(QuizResultCommand command) {
        if (
            command == null
                || command.classroomId() == null
                || command.sessionId() == null
                || command.requestId() == null
                || command.correlationId() == null
                || command.command() == null
                || (command.expectedStateVersion() != null && command.expectedStateVersion() < 0L)
                || (command.expectedResultVersion() != null && command.expectedResultVersion() < 0)
        ) {
            throw validation("Check the quiz result command values.");
        }
    }

    private static void validate(QuizQuestionIssueReportCommand command) {
        if (
            command == null
                || command.classroomId() == null
                || command.sessionId() == null
                || command.questionId() == null
                || command.requestId() == null
                || command.correlationId() == null
                || blank(command.reasonCode())
                || blank(command.description())
                || command.reasonCode().trim().length() > 80
                || command.description().trim().length() > 2_000
                || (command.expectedStateVersion() != null && command.expectedStateVersion() < 0L)
        ) {
            throw validation("Check the quiz question report values.");
        }
    }

    private static void validate(QuizRegradeCommand command) {
        if (
            command == null
                || command.classroomId() == null
                || command.sessionId() == null
                || command.requestId() == null
                || command.correlationId() == null
                || command.invalidatedQuestionIds() == null
                || command.invalidatedQuestionIds().isEmpty()
                || blank(command.reason())
                || command.reason().trim().length() > 2_000
                || command.expectedResultVersion() < 1
                || (command.expectedStateVersion() != null && command.expectedStateVersion() < 0L)
        ) {
            throw validation("Check the quiz regrading values.");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static ClassroomOperationException invalidTransition(String message) {
        return operation(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", message);
    }

    private static ClassroomOperationException validation(String message) {
        return operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }

    private record CommandOutcome(
        QuizResultVersion resultVersion,
        UUID transactionGroupId
    ) {
    }
}
