package com.b201.ecovillage.quiz.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
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
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStatus;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.quiz.domain.QuizOption;
import com.b201.ecovillage.quiz.domain.QuizQuestion;
import com.b201.ecovillage.quiz.domain.QuizQuestionStatus;
import com.b201.ecovillage.quiz.domain.QuizQuestionVersion;
import com.b201.ecovillage.quiz.repository.QuizOptionRepository;
import com.b201.ecovillage.quiz.repository.QuizQuestionRepository;
import com.b201.ecovillage.quiz.repository.QuizQuestionVersionRepository;

@Service
public class QuizQuestionCommandService {

    private static final String EDIT_ROUTE =
        "/classrooms/{classroom_id}/quiz-question-commands";
    private static final String STATUS_ROUTE =
        "/classrooms/{classroom_id}/quiz-questions/{resource_id}/commands";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizQuestionVersionRepository versionRepository;
    private final QuizOptionRepository optionRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public QuizQuestionCommandService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ClassroomRepository classroomRepository,
        QuizQuestionRepository questionRepository,
        QuizQuestionVersionRepository versionRepository,
        QuizOptionRepository optionRepository,
        IdempotencyRecordRepository idempotencyRepository,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.classroomRepository = classroomRepository;
        this.questionRepository = questionRepository;
        this.versionRepository = versionRepository;
        this.optionRepository = optionRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public QuizResourceResult edit(QuizQuestionCommand command) {
        validate(command);
        UserAccount teacher = authorize(
            command.teacherSubject(),
            command.classroomId(),
            command.expectedStateVersion()
        );
        String requestHash = hash(command);
        QuizResourceResult replay = replay(
            command.classroomId(),
            teacher.getId(),
            command.requestId(),
            EDIT_ROUTE,
            requestHash
        );
        if (replay != null) {
            return replay;
        }

        Instant now = clock.instant();
        QuizQuestion question = command.command() == QuizQuestionCommandType.CREATE
            ? create(command, teacher.getId(), now)
            : update(command, now);
        questionRepository.flush();
        QuizResourceResult result = result(question);
        remember(
            command.classroomId(),
            teacher.getId(),
            command.requestId(),
            EDIT_ROUTE,
            requestHash,
            result,
            now
        );
        return result;
    }

    @Transactional
    public QuizResourceResult changeStatus(QuizQuestionStatusCommand command) {
        if (
            command == null
                || command.classroomId() == null
                || command.requestId() == null
                || command.questionId() == null
                || command.command() == null
        ) {
            throw validation("Check the quiz question status command values.");
        }
        UserAccount teacher = authorize(
            command.teacherSubject(),
            command.classroomId(),
            command.expectedStateVersion()
        );
        String requestHash = sha256(
            command.classroomId() + "|" + command.questionId() + "|"
                + command.command() + "|" + normalized(command.reason()) + "|"
                + command.expectedStateVersion()
        );
        QuizResourceResult replay = replay(
            command.classroomId(),
            teacher.getId(),
            command.requestId(),
            STATUS_ROUTE,
            requestHash
        );
        if (replay != null) {
            return replay;
        }

        QuizQuestion question = requireQuestion(
            command.classroomId(),
            command.questionId()
        );
        QuizQuestionVersion latest = latest(command.classroomId(), question.getId());
        Instant now = clock.instant();
        try {
            switch (command.command()) {
                case APPROVE -> {
                    latest.approve();
                    question.approve(now);
                }
                case PUBLISH -> {
                    validateStoredOptions(command.classroomId(), latest);
                    latest.publish(now);
                    question.publish(now);
                }
                case RETIRE -> question.retire(now);
            }
        } catch (IllegalStateException exception) {
            throw operation(
                HttpStatus.CONFLICT,
                "INVALID_STATE_TRANSITION",
                exception.getMessage()
            );
        }
        questionRepository.flush();
        QuizResourceResult result = result(question);
        remember(
            command.classroomId(),
            teacher.getId(),
            command.requestId(),
            STATUS_ROUTE,
            requestHash,
            result,
            now
        );
        return result;
    }

    private QuizQuestion create(
        QuizQuestionCommand command,
        UUID teacherId,
        Instant now
    ) {
        if (command.questionId() != null || command.expectedQuestionVersion() != null) {
            throw validation("CREATE cannot include question_id or expected_question_version.");
        }
        UUID questionId = uuidGenerator.generate();
        QuizQuestion question = questionRepository.save(
            QuizQuestion.draft(
                questionId,
                command.classroomId(),
                teacherId,
                command.contentType(),
                command.prompt(),
                now
            )
        );
        createVersion(command, questionId, 1, now);
        return question;
    }

    private QuizQuestion update(QuizQuestionCommand command, Instant now) {
        if (command.questionId() == null || command.expectedQuestionVersion() == null) {
            throw validation("UPDATE requires question_id and expected_question_version.");
        }
        QuizQuestion question = requireQuestion(
            command.classroomId(),
            command.questionId()
        );
        QuizQuestionVersion latest = latest(command.classroomId(), question.getId());
        if (latest.getVersion() != command.expectedQuestionVersion()) {
            throw operation(
                HttpStatus.CONFLICT,
                "QUESTION_VERSION_CONFLICT",
                "The quiz question version has changed."
            );
        }
        try {
            question.revise(command.contentType(), command.prompt(), now);
        } catch (IllegalStateException exception) {
            throw operation(
                HttpStatus.CONFLICT,
                "INVALID_STATE_TRANSITION",
                exception.getMessage()
            );
        }
        createVersion(command, question.getId(), latest.getVersion() + 1, now);
        return questionRepository.save(question);
    }

    private void createVersion(
        QuizQuestionCommand command,
        UUID questionId,
        int versionNumber,
        Instant now
    ) {
        QuizQuestionVersion version = versionRepository.save(
            QuizQuestionVersion.teacherDraft(
                uuidGenerator.generate(),
                command.classroomId(),
                questionId,
                versionNumber,
                command.contentType(),
                command.questionType(),
                command.prompt(),
                command.explanation(),
                now
            )
        );
        List<QuizOption> options = command.options().stream()
            .sorted(Comparator.comparingInt(QuizOptionCommand::sortOrder))
            .map(option -> QuizOption.create(
                uuidGenerator.generate(),
                command.classroomId(),
                version.getId(),
                option.optionKey(),
                option.optionText(),
                option.correct(),
                option.sortOrder(),
                now
            ))
            .toList();
        optionRepository.saveAll(options);
    }

    private UserAccount authorize(
        String subject,
        UUID classroomId,
        Long expectedStateVersion
    ) {
        authorizationService.authorize(
            subject,
            classroomId,
            ClassroomPermission.MANAGE_QUIZ_QUESTIONS,
            null
        );
        UserAccount teacher = userRepository.findByExternalSubject(subject)
            .orElseThrow(() -> operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A valid teacher session is required."
            ));
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
                "Quiz questions can be changed only in an active classroom."
            );
        }
        if (
            expectedStateVersion != null
                && expectedStateVersion != classroom.getStateVersion()
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The classroom state has changed."
            );
        }
        return teacher;
    }

    private QuizQuestion requireQuestion(UUID classroomId, UUID questionId) {
        return questionRepository.findForUpdate(classroomId, questionId)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The quiz question could not be found."
            ));
    }

    private QuizQuestionVersion latest(UUID classroomId, UUID questionId) {
        return versionRepository
            .findTopByClassroomIdAndQuizQuestionIdOrderByVersionDesc(
                classroomId,
                questionId
            )
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "QUESTION_VERSION_MISSING",
                "The quiz question has no version."
            ));
    }

    private void validateStoredOptions(
        UUID classroomId,
        QuizQuestionVersion version
    ) {
        List<QuizOption> options = optionRepository
            .findAllByClassroomIdAndQuizQuestionVersionIdOrderBySortOrder(
                classroomId,
                version.getId()
            );
        if (options.size() < 2 || options.stream().filter(QuizOption::isCorrect).count() != 1) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "AMBIGUOUS_ANSWER",
                "A published question must have exactly one correct option."
            );
        }
    }

    private static void validate(QuizQuestionCommand command) {
        if (
            command == null
                || command.classroomId() == null
                || command.requestId() == null
                || command.command() == null
                || command.contentType() == null
                || command.questionType() == null
                || command.prompt() == null
                || command.prompt().isBlank()
                || command.options() == null
                || command.options().size() < 2
        ) {
            throw validation("Check the quiz question command values.");
        }
        if (
            command.questionType() == com.b201.ecovillage.quiz.domain.QuizQuestionType.OX
                && command.options().size() != 2
        ) {
            throw validation("An OX question must have exactly two options.");
        }
        Set<String> keys = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        long correctCount = 0;
        for (QuizOptionCommand option : command.options()) {
            if (
                option == null
                    || option.optionKey() == null
                    || option.optionKey().isBlank()
                    || option.optionText() == null
                    || option.optionText().isBlank()
                    || option.sortOrder() < 0
            ) {
                throw validation("Check the quiz option values.");
            }
            if (!keys.add(option.optionKey().trim()) || !sortOrders.add(option.sortOrder())) {
                throw validation("Quiz option keys and sort orders must be unique.");
            }
            if (option.correct()) {
                correctCount++;
            }
        }
        if (correctCount != 1) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "AMBIGUOUS_ANSWER",
                "A quiz question must have exactly one correct option."
            );
        }
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
        QuizQuestion question = questionRepository
            .findByClassroomIdAndId(
                classroomId,
                responseUuid(record.getResponseBody().get("quiz_resource_id"))
            )
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_RESULT_MISSING",
                "The previous quiz question result could not be found."
            ));
        return result(question);
    }

    private void remember(
        UUID classroomId,
        UUID actorId,
        UUID requestId,
        String route,
        String requestHash,
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
                HttpStatus.OK.value(),
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

    private static String hash(QuizQuestionCommand command) {
        String options = command.options().stream()
            .map(option -> option.optionKey() + ":" + option.optionText() + ":"
                + option.correct() + ":" + option.sortOrder())
            .sorted()
            .reduce("", (left, right) -> left + "|" + right);
        return sha256(
            command.classroomId() + "|" + command.command() + "|" + command.questionId()
                + "|" + command.contentType() + "|" + command.questionType() + "|"
                + command.prompt().trim() + "|" + options + "|"
                + normalized(command.explanation()) + "|"
                + command.expectedQuestionVersion() + "|"
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
            throw new IllegalStateException("Could not hash the quiz command.", exception);
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
                "The previous quiz result is invalid."
            );
        }
    }

    private static QuizResourceResult result(QuizQuestion question) {
        return new QuizResourceResult(
            question.getId(),
            question.getStatus().name(),
            question.getStateVersion()
        );
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
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
