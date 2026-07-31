package com.b201.ecovillage.quiz.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.quiz.domain.ActivitySession;
import com.b201.ecovillage.quiz.domain.QuizAnswer;
import com.b201.ecovillage.quiz.domain.QuizMode;
import com.b201.ecovillage.quiz.domain.QuizOption;
import com.b201.ecovillage.quiz.domain.QuizSession;
import com.b201.ecovillage.quiz.domain.QuizSessionQuestion;
import com.b201.ecovillage.quiz.domain.QuizSubmission;
import com.b201.ecovillage.quiz.repository.ActivityParticipantRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;
import com.b201.ecovillage.quiz.repository.QuizAnswerRepository;
import com.b201.ecovillage.quiz.repository.QuizOptionRepository;
import com.b201.ecovillage.quiz.repository.QuizSessionQuestionRepository;
import com.b201.ecovillage.quiz.repository.QuizSessionRepository;
import com.b201.ecovillage.quiz.repository.QuizSubmissionRepository;

@Service
public class QuizResponseService {

    private static final String GAME_ROUTE =
        "/classrooms/{classroom_id}/quiz-sessions/{resource_id}/game-answers";
    private static final String TEST_ROUTE =
        "/classrooms/{classroom_id}/quiz-sessions/{resource_id}/test-answer-sheet";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);

    private final ClassroomAuthorizationService authorizationService;
    private final ActivitySessionRepository activityRepository;
    private final ActivityParticipantRepository participantRepository;
    private final QuizSessionRepository sessionRepository;
    private final QuizSessionQuestionRepository sessionQuestionRepository;
    private final QuizOptionRepository optionRepository;
    private final QuizSubmissionRepository submissionRepository;
    private final QuizAnswerRepository answerRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public QuizResponseService(
        ClassroomAuthorizationService authorizationService,
        ActivitySessionRepository activityRepository,
        ActivityParticipantRepository participantRepository,
        QuizSessionRepository sessionRepository,
        QuizSessionQuestionRepository sessionQuestionRepository,
        QuizOptionRepository optionRepository,
        QuizSubmissionRepository submissionRepository,
        QuizAnswerRepository answerRepository,
        IdempotencyRecordRepository idempotencyRepository,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.activityRepository = activityRepository;
        this.participantRepository = participantRepository;
        this.sessionRepository = sessionRepository;
        this.sessionQuestionRepository = sessionQuestionRepository;
        this.optionRepository = optionRepository;
        this.submissionRepository = submissionRepository;
        this.answerRepository = answerRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public QuizResourceResult answerGame(GameAnswerCommand command) {
        if (
            command == null
                || command.classroomId() == null
                || command.requestId() == null
                || command.sessionId() == null
                || command.questionId() == null
                || command.optionId() == null
                || command.clientAnsweredAt() == null
        ) {
            throw validation("Check the game answer values.");
        }
        UUID studentId = studentId(command.studentSubject());
        authorizeStudent(command.studentSubject(), command.classroomId(), studentId);
        String requestHash = sha256(
            command.classroomId() + "|" + command.sessionId() + "|"
                + command.questionId() + "|" + command.optionId() + "|"
                + command.clientAnsweredAt() + "|" + command.expectedStateVersion()
        );
        QuizResourceResult replay = replay(
            command.classroomId(),
            studentId,
            command.requestId(),
            GAME_ROUTE,
            requestHash
        );
        if (replay != null) {
            return replay;
        }
        QuizSession session = requireOpenSession(
            command.classroomId(),
            command.sessionId(),
            studentId,
            QuizMode.GAME,
            command.expectedStateVersion()
        );
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
        QuizOption option = optionRepository
            .findByClassroomIdAndId(command.classroomId(), command.optionId())
            .filter(value -> value.getQuizQuestionVersionId()
                .equals(question.getQuizQuestionVersionId()))
            .orElseThrow(() -> operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "The selected option does not belong to this question."
            ));
        Instant now = clock.instant();
        QuizSubmission submission = submissionRepository
            .findTopByClassroomIdAndQuizSessionIdAndClassroomStudentIdAndModeOrderByAnswerSheetVersionDesc(
                command.classroomId(),
                command.sessionId(),
                studentId,
                QuizMode.GAME
            )
            .orElseGet(() -> submissionRepository.save(
                QuizSubmission.create(
                    uuidGenerator.generate(),
                    command.classroomId(),
                    command.sessionId(),
                    studentId,
                    QuizMode.GAME,
                    1,
                    false,
                    now
                )
            ));
        if (
            answerRepository
                .existsByClassroomIdAndQuizSubmissionIdAndQuizSessionQuestionId(
                    command.classroomId(),
                    submission.getId(),
                    question.getId()
                )
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "ANSWER_ALREADY_SUBMITTED",
                "This question already has an answer."
            );
        }
        ActivitySession activity = activityRepository
            .findByClassroomIdAndId(command.classroomId(), command.sessionId())
            .orElseThrow();
        int responseTimeMs = responseTime(activity.getStartsAt(), command.clientAnsweredAt());
        boolean correct = question.correctOptionKey().equals(option.getOptionKey());
        QuizAnswer answer = answerRepository.save(
            QuizAnswer.selected(
                uuidGenerator.generate(),
                command.classroomId(),
                submission.getId(),
                question.getId(),
                option.getOptionKey(),
                correct,
                correct ? question.getScoreValue() : 0,
                responseTimeMs,
                now
            )
        );
        QuizResourceResult result = new QuizResourceResult(
            answer.getId(),
            "RECORDED",
            0
        );
        remember(
            command.classroomId(),
            studentId,
            command.requestId(),
            GAME_ROUTE,
            requestHash,
            HttpStatus.CREATED,
            result,
            now
        );
        return result;
    }

    @Transactional
    public QuizResourceResult saveTestSheet(TestAnswerSheetCommand command) {
        if (
            command == null
                || command.classroomId() == null
                || command.requestId() == null
                || command.sessionId() == null
                || command.answers() == null
                || command.answerSheetVersion() < 0
        ) {
            throw validation("Check the test answer sheet values.");
        }
        if (
            new HashSet<>(command.answers().stream()
                .map(TestAnswerCommand::questionId)
                .toList()).size() != command.answers().size()
        ) {
            throw validation("A test question can appear only once in an answer sheet.");
        }
        UUID studentId = studentId(command.studentSubject());
        authorizeStudent(command.studentSubject(), command.classroomId(), studentId);
        String requestHash = sha256(
            command.classroomId() + "|" + command.sessionId() + "|"
                + command.answers() + "|" + command.answerSheetVersion() + "|"
                + command.submit() + "|" + command.expectedStateVersion()
        );
        QuizResourceResult replay = replay(
            command.classroomId(),
            studentId,
            command.requestId(),
            TEST_ROUTE,
            requestHash
        );
        if (replay != null) {
            return replay;
        }
        requireOpenSession(
            command.classroomId(),
            command.sessionId(),
            studentId,
            QuizMode.TEST,
            command.expectedStateVersion()
        );
        int currentVersion = submissionRepository
            .findTopByClassroomIdAndQuizSessionIdAndClassroomStudentIdAndModeOrderByAnswerSheetVersionDesc(
                command.classroomId(),
                command.sessionId(),
                studentId,
                QuizMode.TEST
            )
            .map(QuizSubmission::getAnswerSheetVersion)
            .orElse(0);
        if (currentVersion != command.answerSheetVersion()) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The test answer sheet version has changed."
            );
        }
        Instant now = clock.instant();
        QuizSubmission submission = submissionRepository.save(
            QuizSubmission.create(
                uuidGenerator.generate(),
                command.classroomId(),
                command.sessionId(),
                studentId,
                QuizMode.TEST,
                currentVersion + 1,
                command.submit(),
                now
            )
        );
        for (TestAnswerCommand selected : command.answers()) {
            if (
                selected == null
                    || selected.questionId() == null
                    || selected.optionId() == null
            ) {
                throw validation("Check each test answer value.");
            }
            QuizSessionQuestion question = sessionQuestionRepository
                .findByClassroomIdAndQuizSessionIdAndId(
                    command.classroomId(),
                    command.sessionId(),
                    selected.questionId()
                )
                .orElseThrow(() -> operation(
                    HttpStatus.NOT_FOUND,
                    "RESOURCE_NOT_FOUND",
                    "A test question could not be found."
                ));
            QuizOption option = optionRepository
                .findByClassroomIdAndId(command.classroomId(), selected.optionId())
                .filter(value -> value.getQuizQuestionVersionId()
                    .equals(question.getQuizQuestionVersionId()))
                .orElseThrow(() -> validation(
                    "A selected option does not belong to its test question."
                ));
            boolean correct = question.correctOptionKey().equals(option.getOptionKey());
            answerRepository.save(
                QuizAnswer.selected(
                    uuidGenerator.generate(),
                    command.classroomId(),
                    submission.getId(),
                    question.getId(),
                    option.getOptionKey(),
                    correct,
                    correct ? question.getScoreValue() : 0,
                    null,
                    now
                )
            );
        }
        QuizResourceResult result = new QuizResourceResult(
            submission.getId(),
            submission.getStatus(),
            submission.getAnswerSheetVersion()
        );
        remember(
            command.classroomId(),
            studentId,
            command.requestId(),
            TEST_ROUTE,
            requestHash,
            HttpStatus.OK,
            result,
            now
        );
        return result;
    }

    private QuizSession requireOpenSession(
        UUID classroomId,
        UUID sessionId,
        UUID studentId,
        QuizMode mode,
        Long expectedStateVersion
    ) {
        QuizSession session = sessionRepository.findForUpdate(classroomId, sessionId)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The quiz session could not be found."
            ));
        if (!session.acceptsAnswers()) {
            throw operation(
                HttpStatus.CONFLICT,
                "SUBMISSION_CLOSED",
                "The quiz session is not accepting answers."
            );
        }
        if (session.getMode() != mode) {
            throw operation(
                HttpStatus.CONFLICT,
                "INVALID_QUIZ_MODE",
                "The answer type does not match the quiz mode."
            );
        }
        if (
            expectedStateVersion != null
                && expectedStateVersion != session.getStateVersion()
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The quiz session state has changed."
            );
        }
        if (
            !participantRepository
                .existsByClassroomIdAndActivitySessionIdAndClassroomStudentId(
                    classroomId,
                    sessionId,
                    studentId
                )
        ) {
            throw operation(
                HttpStatus.FORBIDDEN,
                "NOT_PARTICIPANT",
                "The student is not a participant in this quiz."
            );
        }
        return session;
    }

    private void authorizeStudent(String subject, UUID classroomId, UUID studentId) {
        authorizationService.authorize(
            subject,
            classroomId,
            ClassroomPermission.PARTICIPATE_QUIZ,
            studentId
        );
    }

    private QuizResourceResult replay(
        UUID classroomId,
        UUID actorId,
        UUID requestId,
        String route,
        String requestHash
    ) {
        IdempotencyRecord record = idempotencyRepository
            .findForReplay(classroomId, actorId, route, requestId.toString())
            .orElse(null);
        if (record == null) {
            return null;
        }
        if (!record.hasRequestHash(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        Map<String, Object> body = record.getResponseBody();
        return new QuizResourceResult(
            responseUuid(body.get("quiz_resource_id")),
            String.valueOf(body.get("status")),
            Long.parseLong(String.valueOf(body.get("state_version")))
        );
    }

    private void remember(
        UUID classroomId,
        UUID actorId,
        UUID requestId,
        String route,
        String requestHash,
        HttpStatus responseStatus,
        QuizResourceResult result,
        Instant now
    ) {
        idempotencyRepository.save(
            IdempotencyRecord.completed(
                uuidGenerator.generate(),
                classroomId,
                actorId,
                route,
                requestId.toString(),
                requestHash,
                responseStatus.value(),
                Map.of(
                    "quiz_resource_id", result.quizResourceId(),
                    "status", result.status(),
                    "state_version", result.stateVersion()
                ),
                null,
                now.plus(IDEMPOTENCY_TTL),
                now
            )
        );
    }

    private static int responseTime(Instant startsAt, Instant answeredAt) {
        long millis = Math.max(0, Duration.between(startsAt, answeredAt).toMillis());
        return (int) Math.min(Integer.MAX_VALUE, millis);
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

    private static UUID responseUuid(Object value) {
        try {
            return value instanceof UUID uuid
                ? uuid
                : UUID.fromString(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw operation(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_RESULT_MISSING",
                "The previous quiz response is invalid."
            );
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash the quiz answer.", exception);
        }
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
}
