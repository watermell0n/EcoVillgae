package com.b201.ecovillage.ai.api;

import java.security.Principal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.ai.application.AiProposalCommand;
import com.b201.ecovillage.ai.application.AiProposalCommandResult;
import com.b201.ecovillage.ai.application.AiProposalCommandService;
import com.b201.ecovillage.ai.application.AiHistoryQueryService;
import com.b201.ecovillage.ai.application.AiRunHistoryPage;
import com.b201.ecovillage.ai.application.EconomicEventDecisionCommand;
import com.b201.ecovillage.ai.application.EconomicEventDecisionResult;
import com.b201.ecovillage.ai.application.EconomicEventDecisionService;
import com.b201.ecovillage.ai.application.EconomicEventDraftCommand;
import com.b201.ecovillage.ai.application.EconomicEventDraftResult;
import com.b201.ecovillage.ai.application.EconomicEventDraftService;
import com.b201.ecovillage.ai.application.QuizDraftCommand;
import com.b201.ecovillage.ai.application.QuizDraftResult;
import com.b201.ecovillage.ai.application.QuizDraftService;
import com.b201.ecovillage.ai.application.OperationalAnalysisCommand;
import com.b201.ecovillage.ai.application.OperationalAnalysisResult;
import com.b201.ecovillage.ai.application.OperationalAnalysisService;
import com.b201.ecovillage.ai.application.SetupPrecheckOperationCommand;
import com.b201.ecovillage.ai.application.SetupPrecheckOperationResult;
import com.b201.ecovillage.ai.application.SetupPrecheckOperationService;
import com.b201.ecovillage.ai.application.TeacherChatCommand;
import com.b201.ecovillage.ai.application.TeacherChatResult;
import com.b201.ecovillage.ai.application.TeacherChatService;
import com.b201.ecovillage.ai.contract.BalancingContract;
import com.b201.ecovillage.ai.contract.EconomicEventContract;
import com.b201.ecovillage.ai.contract.QuizDraftContract;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;

@RestController
@RequestMapping("/classrooms/{classroomId}/ai")
public class AiController {

    private final SetupPrecheckOperationService precheckService;
    private final AiHistoryQueryService historyQueryService;
    private final OperationalAnalysisService operationalAnalysisService;
    private final AiProposalCommandService proposalCommandService;
    private final EconomicEventDraftService economicEventDraftService;
    private final EconomicEventDecisionService economicEventDecisionService;
    private final QuizDraftService quizDraftService;
    private final TeacherChatService teacherChatService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public AiController(
        SetupPrecheckOperationService precheckService,
        AiHistoryQueryService historyQueryService,
        OperationalAnalysisService operationalAnalysisService,
        AiProposalCommandService proposalCommandService,
        EconomicEventDraftService economicEventDraftService,
        EconomicEventDecisionService economicEventDecisionService,
        QuizDraftService quizDraftService,
        TeacherChatService teacherChatService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.precheckService = precheckService;
        this.historyQueryService = historyQueryService;
        this.operationalAnalysisService = operationalAnalysisService;
        this.proposalCommandService = proposalCommandService;
        this.economicEventDraftService = economicEventDraftService;
        this.economicEventDecisionService = economicEventDecisionService;
        this.quizDraftService = quizDraftService;
        this.teacherChatService = teacherChatService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @GetMapping("/runs-and-proposals")
    public ApiResponse<AiRunsAndProposalsResponse> findRunsAndProposals(
        @PathVariable UUID classroomId,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer limit,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        AiRunHistoryPage page = historyQueryService.findAll(
            principal == null ? null : principal.getName(),
            classroomId,
            cursor,
            limit
        );
        return new ApiResponse<>(
            new AiRunsAndProposalsResponse(page.items()),
            new ApiMeta(
                requestId,
                resolvedCorrelationId,
                clock.instant(),
                new ApiMeta.PageMeta(page.nextCursor(), page.hasMore())
            )
        );
    }

    @PostMapping("/quiz-draft-operations")
    public ResponseEntity<ApiResponse<QuizDraftOperationResponse>> createQuizDraft(
        @PathVariable UUID classroomId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody QuizDraftOperationRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        QuizDraftContract.Category category;
        QuizDraftContract.QuestionType questionType;
        try {
            category = QuizDraftContract.Category.valueOf(request.contentType());
            questionType = QuizDraftContract.QuestionType.valueOf(request.questionType());
        } catch (IllegalArgumentException exception) {
            throw new ClassroomOperationException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "content_type or question_type contains an unsupported value."
            );
        }
        QuizDraftResult result = quizDraftService.create(new QuizDraftCommand(
            principal == null ? null : principal.getName(),
            classroomId,
            requestId,
            resolvedCorrelationId,
            request.grade(),
            request.topic(),
            request.learningObjective(),
            category,
            questionType,
            request.questionCount()
        ));
        QuizDraftOperationResponse response = new QuizDraftOperationResponse(
            result.operationId(),
            result.status(),
            result.statusUrl(),
            result.aiRunId(),
            "REVIEW_REQUIRED",
            result.questionIds()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
            new ApiResponse<>(response, meta(requestId, resolvedCorrelationId))
        );
    }

    @PostMapping("/economic-event-draft-operations")
    public ResponseEntity<ApiResponse<AsyncOperationAcceptedResponse>> createEconomicEventDraft(
        @PathVariable UUID classroomId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody EconomicEventDraftRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        EconomicEventContract.TemplateType templateType;
        EconomicEventContract.InterestRateScope interestRateScope;
        try {
            templateType = EconomicEventContract.TemplateType.valueOf(request.templateType());
            interestRateScope = request.interestRateScope() == null
                ? null
                : EconomicEventContract.InterestRateScope.valueOf(request.interestRateScope());
        } catch (IllegalArgumentException exception) {
            throw new ClassroomOperationException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "template_type or interest_rate_scope contains an unsupported value."
            );
        }
        EconomicEventDraftResult result = economicEventDraftService.create(
            new EconomicEventDraftCommand(
                principal == null ? null : principal.getName(),
                classroomId,
                requestId,
                resolvedCorrelationId,
                templateType,
                interestRateScope,
                request.teacherContext(),
                null
            )
        );
        AsyncOperationAcceptedResponse response = new AsyncOperationAcceptedResponse(
            result.operationId(),
            result.status(),
            result.statusUrl()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
            new ApiResponse<>(response, meta(requestId, resolvedCorrelationId))
        );
    }

    @PostMapping("/economic-events/{eventId}/decisions")
    public ApiResponse<EconomicEventDecisionResponse> decideEconomicEvent(
        @PathVariable UUID classroomId,
        @PathVariable UUID eventId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody EconomicEventDecisionRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        EconomicEventDecisionResult result = economicEventDecisionService.decide(
            new EconomicEventDecisionCommand(
                principal == null ? null : principal.getName(),
                classroomId,
                eventId,
                requestId,
                resolvedCorrelationId,
                request.decision(),
                request.edits(),
                request.reason(),
                request.expectedEventVersion(),
                request.expectedStateVersion()
            )
        );
        EconomicEventDecisionResponse response = new EconomicEventDecisionResponse(
            result.eventId(),
            result.aiRunId(),
            result.status(),
            result.stateVersion(),
            result.publishedAt()
        );
        return new ApiResponse<>(response, meta(requestId, resolvedCorrelationId));
    }

    @PostMapping("/chat-requests")
    public ResponseEntity<ApiResponse<TeacherChatResponse>> createChatRequest(
        @PathVariable UUID classroomId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody TeacherChatRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        Set<BalancingContract.MetricCode> metricCodes;
        try {
            metricCodes = request.metricScopes().stream()
                .map(BalancingContract.MetricCode::valueOf)
                .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException exception) {
            throw new ClassroomOperationException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "metric_scopes contains an unsupported value."
            );
        }
        TeacherChatResult result = teacherChatService.ask(new TeacherChatCommand(
            principal == null ? null : principal.getName(),
            classroomId,
            requestId,
            resolvedCorrelationId,
            request.question(),
            request.period().from(),
            request.period().to(),
            metricCodes,
            Boolean.TRUE.equals(request.includePolicyHistory())
        ));
        TeacherChatResponse response = new TeacherChatResponse(
            result.aiRunId(),
            result.answer(),
            new TeacherChatResponse.PeriodPayload(result.periodFrom(), result.periodTo()),
            result.dataAsOf(),
            result.evidence().stream()
                .map(item -> new TeacherChatResponse.EvidenceItem(
                    item.factId(),
                    item.label(),
                    item.value(),
                    item.unit()
                ))
                .toList(),
            result.recommendations().stream()
                .map(item -> new TeacherChatResponse.RecommendationItem(
                    item.text(),
                    item.sideEffects()
                ))
                .toList()
        );
        return ResponseEntity.ok(
            new ApiResponse<>(response, meta(requestId, resolvedCorrelationId))
        );
    }

    @PostMapping("/balancing-analysis-operations")
    public ResponseEntity<ApiResponse<?>> createBalancingAnalysis(
        @PathVariable UUID classroomId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody SetupPrecheckOperationRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        if (!"SETUP_PRECHECK".equals(request.analysisMode())) {
            return operationalAnalysis(
                classroomId,
                requestId,
                resolvedCorrelationId,
                request,
                principal
            );
        }
        if (request.triggerContext() == null || request.period() != null || !request.metricScopes().isEmpty()) {
            throw new ClassroomOperationException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_RECOMMENDATION_CONTEXT",
                "SETUP_PRECHECK requires only trigger_context."
            );
        }
        SetupPrecheckOperationResult result = precheckService.execute(
            new SetupPrecheckOperationCommand(
                principal == null ? null : principal.getName(),
                classroomId,
                requestId,
                resolvedCorrelationId,
                request.triggerContext()
            )
        );
        SetupPrecheckOperationResponse response = new SetupPrecheckOperationResponse(
            result.operationId(),
            result.aiRunId(),
            result.operationStatus(),
            result.runStatus(),
            result.fallbackUsed(),
            result.fallbackCode(),
            result.overallSeverity().name(),
            result.proposalId(),
            result.summary(),
            result.candidates(),
            result.checks()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
            new ApiResponse<>(response, meta(requestId, resolvedCorrelationId))
        );
    }

    private ResponseEntity<ApiResponse<?>> operationalAnalysis(
        UUID classroomId,
        UUID requestId,
        UUID correlationId,
        SetupPrecheckOperationRequest request,
        Principal principal
    ) {
        BalancingContract.AnalysisMode mode;
        Set<BalancingContract.MetricCode> metricCodes;
        try {
            mode = BalancingContract.AnalysisMode.valueOf(request.analysisMode());
            metricCodes = request.metricScopes().stream()
                .map(BalancingContract.MetricCode::valueOf)
                .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException exception) {
            throw new ClassroomOperationException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "analysis_mode or metric_scopes contains an unsupported value."
            );
        }
        if (request.triggerContext() != null || request.period() == null || metricCodes.isEmpty()) {
            throw new ClassroomOperationException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_RECOMMENDATION_CONTEXT",
                "Operational analysis requires period and metric_scopes without trigger_context."
            );
        }
        OperationalAnalysisResult result = operationalAnalysisService.execute(
            new OperationalAnalysisCommand(
                principal == null ? null : principal.getName(),
                classroomId,
                requestId,
                correlationId,
                mode,
                request.period().from(),
                request.period().to(),
                metricCodes
            )
        );
        OperationalAnalysisResponse response = new OperationalAnalysisResponse(
            result.operationId(),
            result.aiRunId(),
            result.operationStatus(),
            result.runStatus(),
            result.analysisMode(),
            result.analysisSeverity(),
            result.sample(),
            result.insufficientCodes(),
            result.metrics(),
            result.proposalId(),
            result.fallbackUsed(),
            result.fallbackCode(),
            result.summary()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
            new ApiResponse<>(response, meta(requestId, correlationId))
        );
    }

    @PostMapping("/balancing-proposals/{proposalId}/commands")
    public ApiResponse<AiProposalCommandResponse> decideProposal(
        @PathVariable UUID classroomId,
        @PathVariable UUID proposalId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody AiProposalCommandRequest request,
        Principal principal
    ) {
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        Map<String, Long> edits = new LinkedHashMap<>();
        for (AiProposalCommandRequest.EditedValue edit : request.editedValues()) {
            if (edits.putIfAbsent(edit.actionId(), edit.proposedValue()) != null) {
                throw new ClassroomOperationException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "edited_values contains a duplicate action_id."
                );
            }
        }
        AiProposalCommandResult result = proposalCommandService.execute(
            new AiProposalCommand(
                principal == null ? null : principal.getName(),
                classroomId,
                proposalId,
                requestId,
                resolvedCorrelationId,
                request.command(),
                edits,
                request.reason(),
                request.expectedPolicyVersion()
            )
        );
        return new ApiResponse<>(
            new AiProposalCommandResponse(
                result.proposalId(),
                result.aiRunId(),
                result.decisionStatus(),
                result.policyVersion(),
                result.policyVersionId(),
                result.appliedValues(),
                result.stateVersion()
            ),
            meta(requestId, resolvedCorrelationId)
        );
    }

    private ApiMeta meta(UUID requestId, UUID correlationId) {
        return new ApiMeta(requestId, correlationId, clock.instant());
    }
}
