package com.b201.ecovillage.ai.application;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.ai.contract.BalancingContract;
import com.b201.ecovillage.ai.domain.AiArtifact;
import com.b201.ecovillage.ai.domain.AiProposal;
import com.b201.ecovillage.ai.domain.AiRun;
import com.b201.ecovillage.ai.domain.AsyncOperation;
import com.b201.ecovillage.ai.infrastructure.AiServiceCallException;
import com.b201.ecovillage.ai.infrastructure.AiServiceProperties;
import com.b201.ecovillage.ai.infrastructure.BalancingAiClient;
import com.b201.ecovillage.ai.repository.AiArtifactRepository;
import com.b201.ecovillage.ai.repository.AiProposalRepository;
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
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;

import tools.jackson.databind.ObjectMapper;

@Service
public class OperationalAnalysisService {

    private static final String ROUTE =
        "/classrooms/{classroom_id}/ai/balancing-analysis-operations:operational";
    private static final UUID SYSTEM_ACTOR_ID = new UUID(0, 0);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final EconomicMetricAggregationService aggregationService;
    private final EconomicMetricEvaluator evaluator;
    private final BalancingAllowedActionService actionService;
    private final BalancingAiClient aiClient;
    private final BalancingOutputValidator outputValidator;
    private final BalancingFallbackFactory fallbackFactory;
    private final AsyncOperationRepository operationRepository;
    private final AiRunRepository runRepository;
    private final AiArtifactRepository artifactRepository;
    private final AiProposalRepository proposalRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final AiServiceProperties properties;
    private final UuidV7Generator uuidGenerator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OperationalAnalysisService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ClassroomRepository classroomRepository,
        EconomicMetricAggregationService aggregationService,
        EconomicMetricEvaluator evaluator,
        BalancingAllowedActionService actionService,
        BalancingAiClient aiClient,
        BalancingOutputValidator outputValidator,
        BalancingFallbackFactory fallbackFactory,
        AsyncOperationRepository operationRepository,
        AiRunRepository runRepository,
        AiArtifactRepository artifactRepository,
        AiProposalRepository proposalRepository,
        IdempotencyRecordRepository idempotencyRepository,
        AiServiceProperties properties,
        UuidV7Generator uuidGenerator,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.classroomRepository = classroomRepository;
        this.aggregationService = aggregationService;
        this.evaluator = evaluator;
        this.actionService = actionService;
        this.aiClient = aiClient;
        this.outputValidator = outputValidator;
        this.fallbackFactory = fallbackFactory;
        this.operationRepository = operationRepository;
        this.runRepository = runRepository;
        this.artifactRepository = artifactRepository;
        this.proposalRepository = proposalRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.properties = properties;
        this.uuidGenerator = uuidGenerator;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public OperationalAnalysisResult execute(OperationalAnalysisCommand command) {
        return execute(command, false);
    }

    @Transactional
    public OperationalAnalysisResult executeSystem(OperationalAnalysisCommand command) {
        return execute(command, true);
    }

    private OperationalAnalysisResult execute(
        OperationalAnalysisCommand command,
        boolean system
    ) {
        validate(command, system);
        UUID actorId;
        if (system) {
            actorId = SYSTEM_ACTOR_ID;
        } else {
            authorizationService.authorize(
                command.teacherSubject(),
                command.classroomId(),
                ClassroomPermission.REQUEST_AI_ANALYSIS,
                null
            );
            UserAccount teacher = userRepository.findByExternalSubject(command.teacherSubject())
                .orElseThrow(() -> operation(
                    HttpStatus.UNAUTHORIZED,
                    "AUTH_REQUIRED",
                    "A teacher session is required."
                ));
            actorId = teacher.getId();
        }
        String requestHash = hash(command);
        OperationalAnalysisResult replay = replay(command, actorId, requestHash);
        if (replay != null) {
            return replay;
        }
        Classroom classroom = classroomRepository.findById(command.classroomId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The classroom was not found."
            ));
        Instant dataAsOf = clock.instant();
        long seconds = Duration.between(command.periodFrom(), command.periodTo()).toSeconds();
        Instant previousFrom = command.periodFrom().minusSeconds(seconds);
        EconomicMetricSnapshot current = aggregationService.aggregate(
            classroom.getId(),
            command.periodFrom(),
            command.periodTo(),
            dataAsOf,
            command.metricCodes()
        );
        EconomicMetricSnapshot previous = aggregationService.aggregate(
            classroom.getId(),
            previousFrom,
            command.periodFrom(),
            dataAsOf,
            command.metricCodes()
        );
        EconomicMetricEvaluation evaluation = evaluator.evaluate(
            command.periodFrom(),
            command.periodTo(),
            dataAsOf,
            current,
            previous,
            command.metricCodes()
        );
        List<BalancingContract.AllowedAction> actions = evaluation.sufficient()
            ? actionService.find(classroom.getId(), dataAsOf, command.metricCodes())
            : List.of();
        UUID operationId = uuidGenerator.generate();
        UUID aiRunId = uuidGenerator.generate();
        Instant now = clock.instant();
        AsyncOperation asyncOperation = AsyncOperation.running(
            operationId,
            classroom.getId(),
            system ? null : actorId,
            "AI_BALANCING_ANALYSIS",
            now
        );
        BalancingContract.Request request = request(
            command,
            classroom,
            aiRunId,
            evaluation,
            actions,
            now
        );
        AiRun run = AiRun.generating(
            aiRunId,
            classroom.getId(),
            operationId,
            system ? null : actorId,
            command.analysisMode().name(),
            system ? "AUTO_MONITOR" : "TEACHER_REQUEST",
            BalancingContract.PROMPT_VERSION,
            toMap(request.payload()),
            now
        );
        run.setAnalysisPeriod(command.periodFrom(), command.periodTo());
        operationRepository.save(asyncOperation);
        runRepository.save(run);

        if (!evaluation.sufficient()) {
            return completeWithoutModel(
                command,
                actorId,
                requestHash,
                asyncOperation,
                run,
                evaluation,
                "분석에 필요한 학생 수 또는 거래 수가 부족합니다.",
                true,
                now
            );
        }
        boolean availableMetric = evaluation.metrics().stream()
            .anyMatch(metric -> metric.availability() == BalancingContract.Availability.AVAILABLE);
        boolean autoNoGeneration = command.analysisMode() == BalancingContract.AnalysisMode.AUTO_MONITOR
            && (evaluation.severity() == BalancingContract.AnalysisSeverity.NORMAL || actions.isEmpty());
        if (!availableMetric || autoNoGeneration) {
            String summary = !availableMetric
                ? "사용 가능한 운영 지표가 없어 AI 설명을 생성하지 않았습니다."
                : "자동 점검 결과 조정이 필요한 연속 경고와 허용 조정안이 없어 AI를 호출하지 않았습니다.";
            return completeWithoutModel(
                command,
                actorId,
                requestHash,
                asyncOperation,
                run,
                evaluation,
                summary,
                false,
                now
            );
        }

        boolean fallbackUsed = false;
        String fallbackCode = null;
        BalancingContract.Response response;
        try {
            response = aiClient.explain(request);
            outputValidator.validate(request, response);
        } catch (AiServiceCallException exception) {
            fallbackUsed = true;
            fallbackCode = exception.getCode();
            response = fallbackFactory.create(request, fallbackCode);
            outputValidator.validate(request, response);
        }
        Instant completedAt = clock.instant();
        Map<String, Object> validation = validation(evaluation);
        validation.put("fallback_used", fallbackUsed);
        validation.put("fallback_code", fallbackCode);
        validation.put("validation_codes", response.validationCodes());
        run.awaitTeacher(response.provider(), response.modelId(), validation, completedAt);
        artifactRepository.save(AiArtifact.create(
            uuidGenerator.generate(),
            classroom.getId(),
            aiRunId,
            "BALANCING_EXPLANATION",
            toMap(response),
            response.schemaVersion(),
            response.safetyStatus(),
            completedAt
        ));
        AiProposal proposal = createProposal(
            classroom.getId(),
            aiRunId,
            command,
            evaluation,
            request.payload().allowedActions(),
            response.output(),
            completedAt
        );
        if (proposal != null) {
            proposalRepository.save(proposal);
        } else {
            run.complete(completedAt);
        }
        asyncOperation.complete(
            "/classrooms/" + classroom.getId() + "/ai/runs/" + aiRunId,
            completedAt
        );
        OperationalAnalysisResult result = result(
            asyncOperation,
            run,
            command,
            evaluation,
            proposal,
            fallbackUsed,
            fallbackCode,
            response.output().summary()
        );
        remember(command, actorId, requestHash, result, completedAt);
        return result;
    }

    private OperationalAnalysisResult completeWithoutModel(
        OperationalAnalysisCommand command,
        UUID actorId,
        String requestHash,
        AsyncOperation operation,
        AiRun run,
        EconomicMetricEvaluation evaluation,
        String summary,
        boolean insufficient,
        Instant now
    ) {
        Map<String, Object> validation = validation(evaluation);
        if (insufficient) {
            run.insufficientData(validation, now);
        } else {
            run.completeWithoutModel(validation, now);
        }
        artifactRepository.save(AiArtifact.create(
            uuidGenerator.generate(),
            command.classroomId(),
            run.getId(),
            "ECONOMIC_METRIC_SNAPSHOT",
            validation,
            BalancingContract.SCHEMA_VERSION,
            "PASSED",
            now
        ));
        operation.complete(
            "/classrooms/" + command.classroomId() + "/ai/runs/" + run.getId(),
            now
        );
        OperationalAnalysisResult result = result(
            operation,
            run,
            command,
            evaluation,
            null,
            false,
            null,
            summary
        );
        remember(command, actorId, requestHash, result, now);
        return result;
    }

    private BalancingContract.Request request(
        OperationalAnalysisCommand command,
        Classroom classroom,
        UUID aiRunId,
        EconomicMetricEvaluation evaluation,
        List<BalancingContract.AllowedAction> actions,
        Instant now
    ) {
        BalancingContract.TriggerType trigger = command.analysisMode()
            == BalancingContract.AnalysisMode.AUTO_MONITOR
                ? BalancingContract.TriggerType.AUTO_MONITOR
                : BalancingContract.TriggerType.TEACHER_REQUEST;
        return new BalancingContract.Request(
            aiRunId,
            command.requestId(),
            command.correlationId(),
            BalancingContract.SCHEMA_VERSION,
            BalancingContract.PROMPT_VERSION,
            1,
            now.plus(properties.getTotalTimeout()),
            "ko-KR",
            classroom.getGrade(),
            command.analysisMode(),
            trigger,
            new BalancingContract.Payload(
                BalancingContract.METRIC_VERSION,
                BalancingContract.THRESHOLD_VERSION,
                evaluation.period(),
                evaluation.previousPeriod(),
                evaluation.severity(),
                evaluation.sample(),
                evaluation.metrics(),
                actions
            )
        );
    }

    private AiProposal createProposal(
        UUID classroomId,
        UUID aiRunId,
        OperationalAnalysisCommand command,
        EconomicMetricEvaluation evaluation,
        List<BalancingContract.AllowedAction> allowedActions,
        BalancingContract.Output output,
        Instant now
    ) {
        if (output.proposals().isEmpty()) {
            return null;
        }
        BalancingContract.Metric primary = evaluation.metrics().stream()
            .filter(metric -> metric.state() == BalancingContract.MetricState.WARN)
            .findFirst()
            .orElseGet(() -> evaluation.metrics().stream()
                .filter(metric -> metric.state() == BalancingContract.MetricState.OBSERVE)
                .findFirst()
                .orElse(evaluation.metrics().getFirst()));
        String dedupeKey = command.analysisMode() == BalancingContract.AnalysisMode.AUTO_MONITOR
            ? dedupeKey(classroomId, primary, evaluation.period())
            : null;
        if (dedupeKey != null) {
            AiProposal existing = proposalRepository
                .findByClassroomIdAndDedupeKey(classroomId, dedupeKey)
                .orElse(null);
            if (existing != null) {
                return existing;
            }
        }
        Map<String, Object> proposed = new LinkedHashMap<>();
        proposed.put("metric_version", BalancingContract.METRIC_VERSION);
        proposed.put("threshold_version", BalancingContract.THRESHOLD_VERSION);
        proposed.put("period", toMap(evaluation.period()));
        proposed.put("items", output.proposals().stream().map(this::toMap).toList());
        Map<String, Object> allowed = new LinkedHashMap<>();
        allowed.put("items", allowedActions.stream().map(this::toMap).toList());
        return AiProposal.balancing(
            uuidGenerator.generate(),
            classroomId,
            aiRunId,
            command.analysisMode().name(),
            evaluation.severity().name(),
            primary.code().name(),
            primary.direction().name(),
            dedupeKey,
            proposed,
            allowed,
            now
        );
    }

    private String dedupeKey(
        UUID classroomId,
        BalancingContract.Metric metric,
        BalancingContract.Period period
    ) {
        return String.join(
            ":",
            classroomId.toString(),
            metric.code().name(),
            metric.direction().name(),
            metric.state().name(),
            period.from().toString(),
            period.to().toString()
        );
    }

    private Map<String, Object> validation(EconomicMetricEvaluation evaluation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metric_version", BalancingContract.METRIC_VERSION);
        result.put("threshold_version", BalancingContract.THRESHOLD_VERSION);
        result.put("analysis_severity", evaluation.severity().name());
        result.put("sample", toMap(evaluation.sample()));
        result.put("insufficient_codes", evaluation.insufficientCodes());
        result.put("metrics", evaluation.metrics().stream().map(this::toMap).toList());
        return result;
    }

    private OperationalAnalysisResult result(
        AsyncOperation operation,
        AiRun run,
        OperationalAnalysisCommand command,
        EconomicMetricEvaluation evaluation,
        AiProposal proposal,
        boolean fallbackUsed,
        String fallbackCode,
        String summary
    ) {
        return new OperationalAnalysisResult(
            operation.getId(),
            run.getId(),
            operation.getStatus(),
            run.getStatus(),
            command.analysisMode(),
            evaluation.severity(),
            evaluation.sample(),
            evaluation.insufficientCodes(),
            evaluation.metrics(),
            proposal == null ? null : proposal.getId(),
            fallbackUsed,
            fallbackCode,
            summary
        );
    }

    private OperationalAnalysisResult replay(
        OperationalAnalysisCommand command,
        UUID actorId,
        String requestHash
    ) {
        IdempotencyRecord record = idempotencyRepository.findForReplay(
            command.classroomId(),
            actorId,
            ROUTE,
            command.requestId().toString()
        ).orElse(null);
        if (record == null) {
            return null;
        }
        if (!record.hasRequestHash(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        return objectMapper.convertValue(record.getResponseBody(), OperationalAnalysisResult.class);
    }

    private void remember(
        OperationalAnalysisCommand command,
        UUID actorId,
        String requestHash,
        OperationalAnalysisResult result,
        Instant now
    ) {
        idempotencyRepository.save(IdempotencyRecord.completed(
            uuidGenerator.generate(),
            command.classroomId(),
            actorId,
            ROUTE,
            command.requestId().toString(),
            requestHash,
            HttpStatus.ACCEPTED.value(),
            toMap(result),
            null,
            now.plus(IDEMPOTENCY_TTL),
            now
        ));
    }

    private String hash(OperationalAnalysisCommand command) {
        try {
            Map<String, Object> value = new TreeMap<>();
            value.put("classroom_id", command.classroomId().toString());
            value.put("analysis_mode", command.analysisMode().name());
            value.put("period_from", command.periodFrom().toString());
            value.put("period_to", command.periodTo().toString());
            value.put("metric_codes", command.metricCodes().stream()
                .map(Enum::name)
                .sorted()
                .toList());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                objectMapper.writeValueAsBytes(value)
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash the operational analysis command.", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        Map<String, Object> result = objectMapper.convertValue(value, Map.class);
        result.values().removeIf(java.util.Objects::isNull);
        return result;
    }

    private void validate(OperationalAnalysisCommand command, boolean system) {
        if (
            command == null || command.classroomId() == null || command.requestId() == null
                || command.correlationId() == null || command.analysisMode() == null
                || command.periodFrom() == null || command.periodTo() == null
                || command.metricCodes().isEmpty()
        ) {
            throw operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Check the analysis request.");
        }
        if (!command.periodFrom().isBefore(command.periodTo())) {
            throw operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The analysis period is invalid.");
        }
        if (command.periodTo().isAfter(clock.instant())) {
            throw operation(HttpStatus.UNPROCESSABLE_ENTITY, "FUTURE_ANALYSIS_PERIOD", "Future periods cannot be analyzed.");
        }
        if (system != (command.analysisMode() == BalancingContract.AnalysisMode.AUTO_MONITOR)) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_RECOMMENDATION_CONTEXT",
                "AUTO_MONITOR can only be started by the monitoring scheduler."
            );
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
