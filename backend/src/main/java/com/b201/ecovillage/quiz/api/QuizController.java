package com.b201.ecovillage.quiz.api;

import java.security.Principal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.quiz.application.NonsenseImportCommand;
import com.b201.ecovillage.quiz.application.NonsenseImportResult;
import com.b201.ecovillage.quiz.application.NonsenseImportService;
import com.b201.ecovillage.quiz.application.QuizOptionCommand;
import com.b201.ecovillage.quiz.application.QuizQuestionCommand;
import com.b201.ecovillage.quiz.application.QuizQuestionCommandService;
import com.b201.ecovillage.quiz.application.QuizQuestionQueryService;
import com.b201.ecovillage.quiz.application.QuizQuestionStatusCommand;
import com.b201.ecovillage.quiz.application.QuizResourceResult;
import com.b201.ecovillage.quiz.application.QuizQuestionIssueReportCommand;
import com.b201.ecovillage.quiz.application.QuizQuestionIssueResult;
import com.b201.ecovillage.quiz.application.QuizRegradeCommand;
import com.b201.ecovillage.quiz.application.QuizRegradeResult;
import com.b201.ecovillage.quiz.application.QuizResultCommand;
import com.b201.ecovillage.quiz.application.QuizResultQueryService;
import com.b201.ecovillage.quiz.application.QuizResultWorkflowService;
import com.b201.ecovillage.quiz.application.QuizResultsView;
import com.b201.ecovillage.quiz.application.GameAnswerCommand;
import com.b201.ecovillage.quiz.application.QuizResponseService;
import com.b201.ecovillage.quiz.application.QuizSessionCommand;
import com.b201.ecovillage.quiz.application.QuizSessionCommandService;
import com.b201.ecovillage.quiz.application.QuizSessionQueryService;
import com.b201.ecovillage.quiz.application.QuizSessionView;
import com.b201.ecovillage.quiz.application.TestAnswerCommand;
import com.b201.ecovillage.quiz.application.TestAnswerSheetCommand;

@RestController
@RequestMapping("/classrooms/{classroomId}")
public class QuizController {

    private final QuizQuestionCommandService commandService;
    private final QuizQuestionQueryService queryService;
    private final QuizSessionCommandService sessionCommandService;
    private final QuizResponseService responseService;
    private final QuizSessionQueryService sessionQueryService;
    private final QuizResultWorkflowService resultWorkflowService;
    private final QuizResultQueryService resultQueryService;
    private final NonsenseImportService nonsenseImportService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public QuizController(
        QuizQuestionCommandService commandService,
        QuizQuestionQueryService queryService,
        QuizSessionCommandService sessionCommandService,
        QuizResponseService responseService,
        QuizSessionQueryService sessionQueryService,
        QuizResultWorkflowService resultWorkflowService,
        QuizResultQueryService resultQueryService,
        NonsenseImportService nonsenseImportService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.sessionCommandService = sessionCommandService;
        this.responseService = responseService;
        this.sessionQueryService = sessionQueryService;
        this.resultWorkflowService = resultWorkflowService;
        this.resultQueryService = resultQueryService;
        this.nonsenseImportService = nonsenseImportService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @PostMapping("/quiz-sessions/{sessionId}/result-commands")
    public ResponseEntity<ApiResponse<QuizResourceResponse>> commandResult(
        @PathVariable UUID classroomId,
        @PathVariable UUID sessionId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody QuizResultCommandRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = resolve(correlationId);
        QuizResourceResult result = resultWorkflowService.execute(new QuizResultCommand(
            subject(principal),
            classroomId,
            sessionId,
            requestId,
            resolvedCorrelationId,
            request.expectedStateVersion(),
            request.expectedResultVersion(),
            request.command(),
            request.leaderboardPublic()
        ));
        return resourceResponse(result, requestId, resolvedCorrelationId, HttpStatus.OK);
    }

    @PostMapping("/quiz-sessions/{sessionId}/question-issue-reports")
    public ResponseEntity<ApiResponse<QuizQuestionIssueResponse>> reportQuestionIssue(
        @PathVariable UUID classroomId,
        @PathVariable UUID sessionId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody QuizQuestionIssueReportRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = resolve(correlationId);
        QuizQuestionIssueResult result = resultWorkflowService.report(
            new QuizQuestionIssueReportCommand(
                subject(principal),
                classroomId,
                sessionId,
                request.questionId(),
                requestId,
                resolvedCorrelationId,
                request.expectedStateVersion(),
                request.reasonCode(),
                request.description()
            )
        );
        return response(
            HttpStatus.CREATED,
            new QuizQuestionIssueResponse(
                result.issueId(),
                result.status(),
                result.stateVersion()
            ),
            requestId,
            resolvedCorrelationId
        );
    }

    @PostMapping("/quiz-sessions/{sessionId}/regrade-operations")
    public ResponseEntity<ApiResponse<QuizRegradeAcceptedResponse>> regrade(
        @PathVariable UUID classroomId,
        @PathVariable UUID sessionId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody QuizRegradeRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = resolve(correlationId);
        QuizRegradeResult result = resultWorkflowService.regrade(new QuizRegradeCommand(
            subject(principal),
            classroomId,
            sessionId,
            requestId,
            resolvedCorrelationId,
            request.expectedStateVersion(),
            request.expectedResultVersion(),
            request.invalidatedQuestionIds(),
            request.reason()
        ));
        return response(
            HttpStatus.ACCEPTED,
            new QuizRegradeAcceptedResponse(
                result.operationId(),
                result.status(),
                "/operations/" + result.operationId()
            ),
            requestId,
            resolvedCorrelationId
        );
    }

    @GetMapping("/quiz-sessions/{sessionId}/results")
    public ResponseEntity<ApiResponse<QuizResultsResponse>> findResults(
        @PathVariable UUID classroomId,
        @PathVariable UUID sessionId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID resolvedCorrelationId = resolve(correlationId);
        QuizResultsView view = resultQueryService.find(
            subject(principal),
            classroomId,
            sessionId
        );
        return response(
            HttpStatus.OK,
            new QuizResultsResponse(
                view.resultVersion(),
                view.status(),
                view.summary(),
                view.leaderboard().stream()
                    .map(entry -> new QuizLeaderboardEntryResponse(
                        entry.resultId(),
                        "QUIZ_RESULT",
                        view.status(),
                        entry.resultVersion(),
                        0L,
                        entry.rank(),
                        entry.nickname(),
                        entry.characterCode()
                    ))
                    .toList()
            ),
            requestId,
            resolvedCorrelationId
        );
    }

    @PostMapping("/quiz-sessions/{sessionId}/commands")
    public ResponseEntity<ApiResponse<QuizResourceResponse>> commandSession(
        @PathVariable UUID classroomId,
        @PathVariable UUID sessionId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody QuizSessionCommandRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = resolve(correlationId);
        QuizResourceResult result = sessionCommandService.execute(
            new QuizSessionCommand(
                subject(principal),
                classroomId,
                requestId,
                resolvedCorrelationId,
                request.expectedStateVersion(),
                sessionId,
                request.command(),
                request.questionVersionIds(),
                request.participantIds(),
                request.mode(),
                request.contentType(),
                request.rewardPolicyPublic(),
                Boolean.TRUE.equals(request.leaderboardPublic())
            )
        );
        return resourceResponse(result, requestId, resolvedCorrelationId, HttpStatus.OK);
    }

    @PostMapping("/quiz-sessions/{sessionId}/game-answers")
    public ResponseEntity<ApiResponse<QuizResourceResponse>> answerGame(
        @PathVariable UUID classroomId,
        @PathVariable UUID sessionId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody GameAnswerRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = resolve(correlationId);
        QuizResourceResult result = responseService.answerGame(
            new GameAnswerCommand(
                subject(principal),
                classroomId,
                requestId,
                resolvedCorrelationId,
                request.expectedStateVersion(),
                sessionId,
                request.questionId(),
                request.optionId(),
                request.clientAnsweredAt()
            )
        );
        return resourceResponse(
            result,
            requestId,
            resolvedCorrelationId,
            HttpStatus.CREATED
        );
    }

    @PutMapping("/quiz-sessions/{sessionId}/test-answer-sheet")
    public ResponseEntity<ApiResponse<QuizResourceResponse>> saveTestSheet(
        @PathVariable UUID classroomId,
        @PathVariable UUID sessionId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody TestAnswerSheetRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = resolve(correlationId);
        QuizResourceResult result = responseService.saveTestSheet(
            new TestAnswerSheetCommand(
                subject(principal),
                classroomId,
                requestId,
                resolvedCorrelationId,
                request.expectedStateVersion(),
                sessionId,
                request.answers().stream()
                    .map(answer -> new TestAnswerCommand(
                        answer.questionId(),
                        answer.optionId()
                    ))
                    .toList(),
                request.answerSheetVersion(),
                request.submit()
            )
        );
        return resourceResponse(result, requestId, resolvedCorrelationId, HttpStatus.OK);
    }

    @GetMapping("/quiz-sessions/{sessionId}")
    public ResponseEntity<ApiResponse<QuizSessionResponse>> findSession(
        @PathVariable UUID classroomId,
        @PathVariable UUID sessionId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID resolvedCorrelationId = resolve(correlationId);
        QuizSessionView view = sessionQueryService.find(
            subject(principal),
            classroomId,
            sessionId
        );
        return response(
            HttpStatus.OK,
            new QuizSessionResponse(
                view.quizSessionId(),
                view.mode(),
                view.status(),
                view.questions().stream()
                    .map(question -> new QuizSessionQuestionResponse(
                        question.resourceId(),
                        question.label(),
                        question.status(),
                        question.version(),
                        question.stateVersion(),
                        question.prompt(),
                        question.options().stream()
                            .map(option -> new QuizSessionQuestionResponse.Option(
                                option.optionId(),
                                option.optionKey(),
                                option.optionText()
                            ))
                            .toList()
                    ))
                    .toList()
            ),
            requestId,
            resolvedCorrelationId
        );
    }

    private ResponseEntity<ApiResponse<QuizResourceResponse>> resourceResponse(
        QuizResourceResult result,
        UUID requestId,
        UUID correlationId,
        HttpStatus status
    ) {
        return response(
            status,
            new QuizResourceResponse(
                result.quizResourceId(),
                result.status(),
                result.stateVersion()
            ),
            requestId,
            correlationId
        );
    }

    /**
     * 넌센스 문제은행에서 학급으로 문항을 가져온다.
     * AI를 부르지 않으므로 주제와 학습목표를 받지 않고 객관식으로만 만든다.
     */
    @PostMapping("/nonsense-question-imports")
    public ResponseEntity<ApiResponse<NonsenseImportResponse>> importNonsenseQuestions(
        @PathVariable UUID classroomId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody NonsenseImportRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = resolve(correlationId);
        NonsenseImportResult result = nonsenseImportService.importQuestions(
            new NonsenseImportCommand(
                subject(principal),
                classroomId,
                requestId,
                resolvedCorrelationId,
                request.grade(),
                request.questionCount()
            )
        );
        return response(
            HttpStatus.CREATED,
            new NonsenseImportResponse(
                result.questionIds(),
                result.remainingCount(),
                result.bankVersion()
            ),
            requestId,
            resolvedCorrelationId
        );
    }

    @PostMapping("/quiz-question-commands")
    public ResponseEntity<ApiResponse<QuizResourceResponse>> editQuestion(
        @PathVariable UUID classroomId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody QuizQuestionCommandRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = resolve(correlationId);
        QuizResourceResult result = commandService.edit(
            new QuizQuestionCommand(
                subject(principal),
                classroomId,
                requestId,
                resolvedCorrelationId,
                request.expectedStateVersion(),
                request.command(),
                request.questionId(),
                request.contentType(),
                request.questionType(),
                request.prompt(),
                request.options().stream()
                    .map(option -> new QuizOptionCommand(
                        option.optionKey(),
                        option.optionText(),
                        option.isCorrect(),
                        option.sortOrder()
                    ))
                    .toList(),
                request.explanation(),
                request.expectedQuestionVersion()
            )
        );
        return response(
            HttpStatus.OK,
            new QuizResourceResponse(
                result.quizResourceId(),
                result.status(),
                result.stateVersion()
            ),
            requestId,
            resolvedCorrelationId
        );
    }

    @PostMapping("/quiz-questions/{questionId}/commands")
    public ResponseEntity<ApiResponse<QuizResourceResponse>> changeQuestionStatus(
        @PathVariable UUID classroomId,
        @PathVariable UUID questionId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody QuizQuestionStatusCommandRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = resolve(correlationId);
        QuizResourceResult result = commandService.changeStatus(
            new QuizQuestionStatusCommand(
                subject(principal),
                classroomId,
                requestId,
                resolvedCorrelationId,
                request.expectedStateVersion(),
                questionId,
                request.command(),
                request.reason()
            )
        );
        return response(
            HttpStatus.OK,
            new QuizResourceResponse(
                result.quizResourceId(),
                result.status(),
                result.stateVersion()
            ),
            requestId,
            resolvedCorrelationId
        );
    }

    @GetMapping("/quiz-questions")
    public ResponseEntity<ApiResponse<QuizQuestionsResponse>> findQuestions(
        @PathVariable UUID classroomId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID resolvedCorrelationId = resolve(correlationId);
        List<QuizQuestionResponse> questions = queryService
            .findAll(subject(principal), classroomId)
            .stream()
            .map(view -> new QuizQuestionResponse(
                view.resourceId(),
                view.versionId(),
                view.label(),
                view.status(),
                view.version(),
                view.questionVersionId(),
                view.stateVersion(),
                view.contentType(),
                view.questionType(),
                view.prompt(),
                view.explanation(),
                view.source(),
                view.aiRunId(),
                view.alreadyUsedInSession(),
                view.options().stream()
                    .map(option -> new QuizQuestionResponse.OptionResponse(
                        option.optionId(),
                        option.optionKey(),
                        option.optionText(),
                        option.correct(),
                        option.sortOrder()
                    ))
                    .toList()
            ))
            .toList();
        return response(
            HttpStatus.OK,
            new QuizQuestionsResponse(questions),
            requestId,
            resolvedCorrelationId
        );
    }

    private UUID resolve(UUID correlationId) {
        return correlationId == null ? uuidGenerator.generate() : correlationId;
    }

    private <T> ResponseEntity<ApiResponse<T>> response(
        HttpStatus status,
        T data,
        UUID requestId,
        UUID correlationId
    ) {
        return ResponseEntity.status(status)
            .body(new ApiResponse<>(
                data,
                new ApiMeta(requestId, correlationId, clock.instant())
            ));
    }

    private static String subject(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
