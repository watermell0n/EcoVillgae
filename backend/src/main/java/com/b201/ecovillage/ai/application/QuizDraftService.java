package com.b201.ecovillage.ai.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.ai.contract.QuizDraftContract;
import com.b201.ecovillage.ai.domain.AiArtifact;
import com.b201.ecovillage.ai.domain.AiRun;
import com.b201.ecovillage.ai.domain.AsyncOperation;
import com.b201.ecovillage.ai.infrastructure.AiServiceCallException;
import com.b201.ecovillage.ai.infrastructure.AiServiceProperties;
import com.b201.ecovillage.ai.infrastructure.QuizDraftAiClient;
import com.b201.ecovillage.ai.repository.AiArtifactRepository;
import com.b201.ecovillage.ai.repository.AiRunRepository;
import com.b201.ecovillage.ai.repository.AsyncOperationRepository;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.quiz.application.QuizOptionCommand;
import com.b201.ecovillage.quiz.application.QuizQuestionCommand;
import com.b201.ecovillage.quiz.application.QuizQuestionCommandService;
import com.b201.ecovillage.quiz.application.QuizQuestionCommandType;
import com.b201.ecovillage.quiz.application.QuizResourceResult;
import com.b201.ecovillage.quiz.domain.QuizContentType;

import tools.jackson.databind.ObjectMapper;

/**
 * API-QZ-002 교사용 AI 퀴즈 초안 생성을 담당한다.
 * 생성된 문항은 기존 문제은행 명령 서비스를 그대로 호출해 DRAFT로 저장하며
 * 교사 승인 전에는 공개하지 않는다.
 */
@Service
public class QuizDraftService {

    static final String RUN_TYPE = "QUIZ_DRAFT";
    static final int DAILY_LIMIT = 30;
    private static final String OPERATION_TYPE = "AI_QUIZ_DRAFT";

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final QuizDraftAiClient aiClient;
    private final ClassroomAiSettings aiSettings;
    private final QuizQuestionCommandService quizQuestionCommandService;
    private final AsyncOperationRepository operationRepository;
    private final AiRunRepository runRepository;
    private final AiArtifactRepository artifactRepository;
    private final AiServiceProperties properties;
    private final UuidV7Generator uuidGenerator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public QuizDraftService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ClassroomRepository classroomRepository,
        QuizDraftAiClient aiClient,
        ClassroomAiSettings aiSettings,
        QuizQuestionCommandService quizQuestionCommandService,
        AsyncOperationRepository operationRepository,
        AiRunRepository runRepository,
        AiArtifactRepository artifactRepository,
        AiServiceProperties properties,
        UuidV7Generator uuidGenerator,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.classroomRepository = classroomRepository;
        this.aiClient = aiClient;
        this.aiSettings = aiSettings;
        this.quizQuestionCommandService = quizQuestionCommandService;
        this.operationRepository = operationRepository;
        this.runRepository = runRepository;
        this.artifactRepository = artifactRepository;
        this.properties = properties;
        this.uuidGenerator = uuidGenerator;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public QuizDraftResult create(QuizDraftCommand command) {
        validate(command);
        authorizationService.authorize(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.MANAGE_QUIZ_QUESTIONS,
            null
        );
        UserAccount teacher = userRepository.findByExternalSubject(command.teacherSubject())
            .orElseThrow(() -> operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A teacher session is required."
            ));
        Classroom classroom = classroomRepository.findById(command.classroomId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The classroom was not found."
            ));

        Instant now = clock.instant();
        requireDailyQuota(teacher.getId(), now);
        if (!aiSettings.enabled(classroom.getId())) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "AI_DISABLED",
                "This classroom turned the AI features off."
            );
        }

        UUID operationId = uuidGenerator.generate();
        UUID aiRunId = uuidGenerator.generate();
        QuizDraftContract.Request request = new QuizDraftContract.Request(
            aiRunId,
            command.requestId(),
            command.correlationId(),
            QuizDraftContract.SCHEMA_VERSION,
            QuizDraftContract.PROMPT_VERSION,
            1,
            now.plus(properties.getTotalTimeout()),
            QuizDraftContract.LOCALE,
            command.grade(),
            new QuizDraftContract.Payload(
                command.topic(),
                difficulty(command.grade()),
                List.of(command.topic()),
                List.of(command.learningObjective()),
                command.category(),
                command.questionType(),
                command.questionCount()
            )
        );

        AsyncOperation asyncOperation = operationRepository.save(AsyncOperation.running(
            operationId,
            classroom.getId(),
            teacher.getId(),
            OPERATION_TYPE,
            now
        ));
        AiRun run = runRepository.save(AiRun.generating(
            aiRunId,
            classroom.getId(),
            operationId,
            teacher.getId(),
            RUN_TYPE,
            "TEACHER_REQUEST",
            QuizDraftContract.PROMPT_VERSION,
            Map.of(
                "category", command.category().name(),
                "question_type", command.questionType().name(),
                "question_count", command.questionCount()
            ),
            now
        ));

        QuizDraftContract.Response response;
        try {
            response = aiClient.draft(request);
            verifyOutput(request, response);
        } catch (AiServiceCallException exception) {
            // 퀴즈 문항은 규칙 엔진이 대신 만들 수 없어 fallback이 없다.
            // 예외를 던지면 이 트랜잭션의 실행·작업 기록도 함께 롤백되므로 별도 실패 상태를 남기지 않는다.
            throw operation(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getCode(),
                "The AI quiz draft could not be generated."
            );
        }

        List<UUID> questionIds = persistDrafts(command, response);

        Instant completedAt = clock.instant();
        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("question_count", response.output().questions().size());
        validation.put("review_status", response.output().reviewStatus());
        validation.put("validation_codes", response.validationCodes());
        run.completeWithModel(
            response.provider(),
            response.modelId(),
            validation,
            completedAt
        );
        artifactRepository.save(AiArtifact.create(
            uuidGenerator.generate(),
            classroom.getId(),
            aiRunId,
            "QUIZ_DRAFT",
            toMap(response),
            response.schemaVersion(),
            response.safetyStatus(),
            completedAt
        ));
        String statusUrl = "/operations/" + operationId;
        asyncOperation.complete(statusUrl, completedAt);

        return new QuizDraftResult(
            operationId,
            "RUNNING",
            statusUrl,
            aiRunId,
            questionIds,
            false,
            null
        );
    }

    /**
     * 기존 문제은행 명령 서비스를 그대로 호출한다. 상태 전이와 버전 관리는 퀴즈 도메인이 소유한다.
     */
    private List<UUID> persistDrafts(QuizDraftCommand command, QuizDraftContract.Response response) {
        List<UUID> questionIds = new ArrayList<>();
        for (QuizDraftContract.QuestionDraft question : response.output().questions()) {
            List<QuizOptionCommand> options = new ArrayList<>();
            int sortOrder = 1;
            for (QuizDraftContract.Choice choice : question.choices()) {
                options.add(new QuizOptionCommand(
                    choice.choiceKey(),
                    choice.text(),
                    choice.choiceKey().equals(question.correctChoiceKey()),
                    sortOrder++
                ));
            }
            QuizResourceResult created = quizQuestionCommandService.edit(new QuizQuestionCommand(
                command.teacherSubject(),
                command.classroomId(),
                uuidGenerator.generate(),
                command.correlationId(),
                null,
                QuizQuestionCommandType.CREATE,
                null,
                QuizContentType.valueOf(response.output().category().name()),
                com.b201.ecovillage.quiz.domain.QuizQuestionType.valueOf(
                    question.questionType().name()
                ),
                question.question(),
                options,
                question.explanation(),
                null
            ));
            questionIds.add(created.quizResourceId());
        }
        return questionIds;
    }

    /**
     * AI가 분류·검토 상태·문항 수를 바꾸면 신뢰할 수 없으므로 저장하지 않는다.
     */
    private void verifyOutput(QuizDraftContract.Request request, QuizDraftContract.Response response) {
        QuizDraftContract.Output output = response.output();
        if (!"REVIEW_REQUIRED".equals(output.reviewStatus())) {
            throw new AiServiceCallException(
                "AI_OUTPUT_CONTRACT_VIOLATION",
                "The AI quiz output must stay in REVIEW_REQUIRED."
            );
        }
        if (output.category() != request.payload().category()) {
            throw new AiServiceCallException(
                "AI_OUTPUT_CONTRACT_VIOLATION",
                "The AI quiz output changed the category."
            );
        }
        if (output.questions().size() != request.payload().count()) {
            throw new AiServiceCallException(
                "AI_OUTPUT_CONTRACT_VIOLATION",
                "The AI quiz output returned a different question count."
            );
        }
        for (QuizDraftContract.QuestionDraft question : output.questions()) {
            if (question.questionType() != request.payload().questionType()) {
                throw new AiServiceCallException(
                    "AI_OUTPUT_CONTRACT_VIOLATION",
                    "The AI quiz output changed the question type."
                );
            }
            boolean correctExists = question.choices().stream()
                .anyMatch(choice -> choice.choiceKey().equals(question.correctChoiceKey()));
            if (!correctExists) {
                throw new AiServiceCallException(
                    "AI_OUTPUT_CONTRACT_VIOLATION",
                    "The AI quiz answer key is not present in the choices."
                );
            }
        }
    }

    private void requireDailyQuota(UUID teacherId, Instant now) {
        long used = runRepository.countByRequestedByUserIdAndRunTypeAndRequestedAtGreaterThanEqual(
            teacherId,
            RUN_TYPE,
            now.truncatedTo(ChronoUnit.DAYS)
        );
        if (used >= DAILY_LIMIT) {
            throw operation(
                HttpStatus.TOO_MANY_REQUESTS,
                "AI_DAILY_LIMIT_EXCEEDED",
                "The daily AI quiz draft limit was reached."
            );
        }
    }

    private static QuizDraftContract.Difficulty difficulty(int grade) {
        return switch (grade) {
            case 4 -> QuizDraftContract.Difficulty.INTRODUCTORY;
            case 5 -> QuizDraftContract.Difficulty.STANDARD;
            default -> QuizDraftContract.Difficulty.ADVANCED;
        };
    }

    private void validate(QuizDraftCommand command) {
        if (command.teacherSubject() == null || command.teacherSubject().isBlank()) {
            throw operation(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "A teacher session is required.");
        }
        if (command.grade() < 4 || command.grade() > 6) {
            throw operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "grade must be 4, 5, or 6.");
        }
        if (command.topic() == null || command.topic().isBlank()) {
            throw operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "topic must not be blank.");
        }
        if (command.learningObjective() == null || command.learningObjective().isBlank()) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "learning_objective must not be blank."
            );
        }
        if (command.questionCount() < 1 || command.questionCount() > 30) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "question_count must be between 1 and 30."
            );
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, Map.class);
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
