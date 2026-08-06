package com.b201.ecovillage.ai.application;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HexFormat;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.ai.contract.SetupPrecheckContract;
import com.b201.ecovillage.ai.domain.AiArtifact;
import com.b201.ecovillage.ai.domain.AiProposal;
import com.b201.ecovillage.ai.domain.AiRun;
import com.b201.ecovillage.ai.domain.AsyncOperation;
import com.b201.ecovillage.ai.infrastructure.AiServiceCallException;
import com.b201.ecovillage.ai.infrastructure.AiServiceProperties;
import com.b201.ecovillage.ai.infrastructure.SetupPrecheckAiClient;
import com.b201.ecovillage.ai.repository.AiArtifactRepository;
import com.b201.ecovillage.ai.repository.AiProposalRepository;
import com.b201.ecovillage.ai.repository.AiRunRepository;
import com.b201.ecovillage.ai.repository.AsyncOperationRepository;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.investment.domain.InvestmentAssetCatalog;
import com.b201.ecovillage.investment.domain.InvestmentMode;
import com.b201.ecovillage.investment.domain.InvestmentModeConfig;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.ruleengine.application.RuleEngineService;
import com.b201.ecovillage.ruleengine.domain.RuleCandidate;
import com.b201.ecovillage.ruleengine.domain.RuleEngineRequest;
import com.b201.ecovillage.ruleengine.domain.RuleEngineResult;

import tools.jackson.databind.ObjectMapper;

@Service
public class SetupPrecheckOperationService {

    private static final String ROUTE =
        "/classrooms/{classroom_id}/ai/balancing-analysis-operations";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomModuleRepository moduleRepository;
    private final ClassroomPolicyVersionRepository policyVersionRepository;
    private final RuleEngineService ruleEngineService;
    private final InvestmentSetupMetricsProvider investmentMetricsProvider;
    private final SetupProposalTargetSnapshotService targetSnapshotService;
    private final SetupPrecheckContractMapper contractMapper;
    private final SetupPrecheckAiClient aiClient;
    private final SetupPrecheckOutputValidator outputValidator;
    private final SetupPrecheckFallbackFactory fallbackFactory;
    private final ClassroomAiSettings aiSettings;
    private final AsyncOperationRepository operationRepository;
    private final AiRunRepository runRepository;
    private final AiArtifactRepository artifactRepository;
    private final AiProposalRepository proposalRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final AiServiceProperties properties;
    private final UuidV7Generator uuidGenerator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SetupPrecheckOperationService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ClassroomRepository classroomRepository,
        ClassroomModuleRepository moduleRepository,
        ClassroomPolicyVersionRepository policyVersionRepository,
        RuleEngineService ruleEngineService,
        InvestmentSetupMetricsProvider investmentMetricsProvider,
        SetupProposalTargetSnapshotService targetSnapshotService,
        SetupPrecheckContractMapper contractMapper,
        SetupPrecheckAiClient aiClient,
        SetupPrecheckOutputValidator outputValidator,
        SetupPrecheckFallbackFactory fallbackFactory,
        ClassroomAiSettings aiSettings,
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
        this.moduleRepository = moduleRepository;
        this.policyVersionRepository = policyVersionRepository;
        this.ruleEngineService = ruleEngineService;
        this.investmentMetricsProvider = investmentMetricsProvider;
        this.targetSnapshotService = targetSnapshotService;
        this.contractMapper = contractMapper;
        this.aiClient = aiClient;
        this.outputValidator = outputValidator;
        this.fallbackFactory = fallbackFactory;
        this.aiSettings = aiSettings;
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
    public SetupPrecheckOperationResult execute(SetupPrecheckOperationCommand command) {
        validate(command);
        authorizationService.authorize(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.REQUEST_AI_ANALYSIS,
            null
        );
        UserAccount teacher = userRepository.findByExternalSubject(command.teacherSubject())
            .orElseThrow(() -> operation(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "A teacher session is required."));
        String requestHash = hash(command);
        SetupPrecheckOperationResult replay = replay(command, teacher.getId(), requestHash);
        if (replay != null) {
            return replay;
        }
        Classroom classroom = classroomRepository.findById(command.classroomId())
            .orElseThrow(() -> operation(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "The classroom was not found."));

        RuleEngineRequest ruleRequest = ruleRequest(classroom, command.input());
        RuleEngineResult rules = ruleEngineService.evaluate(ruleRequest);
        UUID operationId = uuidGenerator.generate();
        UUID aiRunId = uuidGenerator.generate();
        Instant now = clock.instant();
        AsyncOperation operationDraft = AsyncOperation.running(
            operationId,
            classroom.getId(),
            teacher.getId(),
            "AI_BALANCING_ANALYSIS",
            now
        );
        SetupPrecheckContract.Request request = contractMapper.toRequest(
            aiRunId,
            command.requestId(),
            command.correlationId(),
            now.plus(properties.getTotalTimeout()),
            classroom,
            ruleRequest,
            rules
        );
        AiRun runDraft = AiRun.generating(
            aiRunId,
            classroom.getId(),
            operationId,
            teacher.getId(),
            "SETUP_PRECHECK",
            "TEACHER_REQUEST",
            SetupPrecheckContract.PROMPT_VERSION,
            toMap(request.payload()),
            now
        );
        // @Version이 primitive라 save()가 merge로 동작한다. 이후 상태 변경이 반영되도록
        // 반환된 관리 인스턴스를 사용한다.
        AsyncOperation asyncOperation = operationRepository.save(operationDraft);
        AiRun run = runRepository.save(runDraft);

        boolean fallbackUsed = false;
        String fallbackCode = null;
        SetupPrecheckContract.Response response;
        if (!aiSettings.enabled(classroom.getId())) {
            fallbackUsed = true;
            fallbackCode = "AI_DISABLED_FOR_CLASSROOM";
            response = fallbackFactory.create(request, rules.ruleVersion(), fallbackCode);
            outputValidator.validate(request, response);
        } else {
            try {
                response = aiClient.explain(request);
                outputValidator.validate(request, response);
            } catch (AiServiceCallException exception) {
                fallbackUsed = true;
                fallbackCode = exception.getCode();
                response = fallbackFactory.create(request, rules.ruleVersion(), fallbackCode);
                outputValidator.validate(request, response);
            }
        }

        Instant completedAt = clock.instant();
        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("rule_version", rules.ruleVersion());
        validation.put("overall_severity", rules.overallSeverity().name());
        validation.put("fallback_used", fallbackUsed);
        validation.put("fallback_code", fallbackCode);
        validation.put("validation_codes", response.validationCodes());
        run.awaitTeacher(response.provider(), response.modelId(), validation, completedAt);
        artifactRepository.save(AiArtifact.create(
            uuidGenerator.generate(),
            classroom.getId(),
            aiRunId,
            "SETUP_PRECHECK_EXPLANATION",
            toMap(response),
            response.schemaVersion(),
            response.safetyStatus(),
            completedAt
        ));
        AiProposal proposal = createProposal(
            classroom,
            command.input(),
            rules,
            aiRunId,
            completedAt
        );
        if (proposal != null) {
            proposalRepository.save(proposal);
        }
        asyncOperation.complete(
            "/classrooms/" + classroom.getId() + "/ai/runs/" + aiRunId,
            completedAt
        );
        SetupPrecheckOperationResult result = new SetupPrecheckOperationResult(
            operationId,
            aiRunId,
            asyncOperation.getStatus(),
            run.getStatus(),
            fallbackUsed,
            fallbackCode,
            response.output().overallSeverity(),
            proposal == null ? null : proposal.getId(),
            response.output().summary(),
            response.output().candidateExplanations(),
            response.output().checkExplanations()
        );
        remember(command, teacher.getId(), requestHash, result, completedAt);
        return result;
    }

    private SetupPrecheckOperationResult replay(
        SetupPrecheckOperationCommand command,
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
        return objectMapper.convertValue(
            record.getResponseBody(),
            SetupPrecheckOperationResult.class
        );
    }

    private void remember(
        SetupPrecheckOperationCommand command,
        UUID actorId,
        String requestHash,
        SetupPrecheckOperationResult result,
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

    private String hash(SetupPrecheckOperationCommand command) {
        try {
            Map<String, Object> value = new java.util.TreeMap<>();
            value.put("classroom_id", command.classroomId().toString());
            value.put("input", toMap(command.input()));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                objectMapper.writeValueAsBytes(value)
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash the setup precheck command.", exception);
        }
    }

    private RuleEngineRequest ruleRequest(Classroom classroom, SetupPrecheckInput input) {
        Set<String> activeModules = moduleRepository
            .findAllByClassroomIdOrderByModuleCode(classroom.getId())
            .stream()
            .filter(module -> Set.of("ACTIVE", "PENDING_CONFIGURATION").contains(module.getStatus()))
            .map(ClassroomModule::getModuleCode)
            .collect(Collectors.toSet());
        RuleEngineRequest.Builder builder = RuleEngineRequest.builder(
            input.contextType(),
            classroom.getGrade(),
            classroom.getActualStartsOn(),
            classroom.getActualEndsOn()
        )
            .activeStudentCount(value(input.activeStudentCount(), classroom.getPlannedStudentCount()))
            .currencyName(classroom.getCurrencyName())
            .activeModules(activeModules)
            .systemAmountCap(value(input.systemAmountCap(), 1_000_000L))
            .budget(
                value(input.nonRewardIssuance(), 0L),
                value(input.existingRewardIssuance(), 0L),
                value(input.expectedRecovery(), 0L)
            )
            .otherPeriodicIssuancePerStudent(input.otherPeriodicIssuancePerStudent())
            .effectiveTaxBps(input.effectiveTaxBps());
        if (input.moduleActivation() != null) {
            SetupPrecheckInput.ModuleActivation value = input.moduleActivation();
            ClassroomModule module = moduleRepository
                .findByClassroomIdAndModuleCode(
                    classroom.getId(),
                    normalizeModule(value.moduleCode())
                )
                .orElseThrow(() -> operation(
                    HttpStatus.NOT_FOUND,
                    "RESOURCE_NOT_FOUND",
                    "The setup target module was not found."
                ));
            if (value.targetId() != null && !value.targetId().equals(module.getId())) {
                throw operation(
                    HttpStatus.CONFLICT,
                    "INVALID_RECOMMENDATION_CONTEXT",
                    "The module target does not match the classroom module."
                );
            }
            builder.moduleActivation(new RuleEngineRequest.ModuleActivation(
                module.getModuleCode(),
                module.getId(),
                value.confirmedValue(),
                // 대출 금리 추천은 현재 예금 기준금리를 전제로 한다. 화면이 그 값을 알 수 없으므로
                // 요청에 없으면 확정된 DEPOSIT_RATE 정책에서 직접 읽는다.
                value.currentDepositRateBps() == null
                    ? currentDepositRateBps(classroom.getId(), module.getModuleCode())
                    : value.currentDepositRateBps()
            ));
            if (input.investmentDraft() != null) {
                builder.investmentDraft(investmentDraft(
                    classroom.getId(), module.getId(), input.investmentDraft()
                ));
            }
        }
        if (input.salaryPolicy() != null) {
            SetupPrecheckInput.SalaryPolicy policy = input.salaryPolicy();
            builder.salaryPolicy(new RuleEngineRequest.SalaryPolicy(
                policy.roles().stream().map(role -> new RuleEngineRequest.SalaryRole(
                    role.roleCode(),
                    role.weight(),
                    role.confirmedSalary(),
                    value(role.assignedStudentCount(), 0),
                    !Boolean.FALSE.equals(role.active())
                )).toList(),
                policy.payIntervalDays(),
                policy.firstPayOn(),
                policy.defaultRoleSalary()
            ));
        }
        if (input.productDraft() != null) {
            SetupPrecheckInput.ProductDraft draft = input.productDraft();
            builder.productDraft(new RuleEngineRequest.ProductDraft(
                draft.targetId(),
                draft.kind(),
                value(draft.stock(), 0),
                value(draft.expectedDemand(), 0),
                draft.expectedPurchaseFrequency(),
                value(draft.vatBps(), 0),
                Boolean.TRUE.equals(draft.free()),
                draft.confirmedPrice()
            ));
        }
        if (input.quizDraft() != null) {
            SetupPrecheckInput.QuizDraft draft = input.quizDraft();
            builder.quizDraft(new RuleEngineRequest.QuizDraft(
                draft.targetId(),
                draft.mode(),
                draft.category(),
                value(draft.questionCount(), 0),
                draft.expectedParticipants(),
                draft.expectedRuns(),
                draft.confirmedRewardPerPerson()
            ));
        }
        return builder.build();
    }

    /**
     * 투자 조건 추천에 넘길 입력입니다. 종목 목록은 서버가 가진 목록을 그대로 쓰고, 학급
     * 지표(발행액·학생 잔액)는 원장에서 직접 읽습니다. 화면은 지금 적혀 있는 값만 보냅니다.
     */
    private RuleEngineRequest.InvestmentDraft investmentDraft(
        UUID classroomId,
        UUID moduleId,
        SetupPrecheckInput.InvestmentDraft draft
    ) {
        Map<String, SetupPrecheckInput.AssetCondition> confirmed = new LinkedHashMap<>();
        for (SetupPrecheckInput.AssetCondition condition : draft.confirmedAssetConditions()) {
            if (condition != null && condition.symbol() != null) {
                confirmed.put(condition.symbol().trim().toUpperCase(Locale.ROOT), condition);
            }
        }
        InvestmentSetupMetricsProvider.InvestmentSetupMetrics metrics =
            investmentMetricsProvider.of(classroomId);
        // 방식마다 쓰는 종목 수가 다르다. 시나리오 생성과 같은 목록을 봐야 교사가 정한 값이
        // 빠짐없이 종목에 붙는다.
        InvestmentMode mode = InvestmentModeConfig.parse(draft.investmentMode())
            .orElse(InvestmentMode.LONG_TERM);
        return new RuleEngineRequest.InvestmentDraft(
            moduleId,
            draft.investmentMode(),
            metrics.moneyIssuance(),
            metrics.averageBalance(),
            metrics.medianBalance(),
            InvestmentAssetCatalog.seeds(mode).stream()
                .map(seed -> {
                    SetupPrecheckInput.AssetCondition condition = confirmed.get(seed.symbol());
                    return new RuleEngineRequest.InvestmentAsset(
                        seed.symbol(), seed.name(), seed.priceWeight(), seed.riskWeight(),
                        condition == null ? null : condition.price(),
                        condition == null ? null : condition.bandBps()
                    );
                })
                .toList()
        );
    }

    /**
     * 확정된 예금 기준금리(bps)입니다. LOAN 외의 모듈에는 필요하지 않으며, 예금 정책이 아직
     * 없으면 null을 돌려주어 규칙 엔진이 "예금 기준금리가 필요합니다"로 안내하게 둡니다.
     */
    private Integer currentDepositRateBps(UUID classroomId, String moduleCode) {
        if (!"LOAN".equals(moduleCode)) {
            return null;
        }
        return policyVersionRepository
            .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(classroomId, "DEPOSIT_RATE")
            .map(ClassroomPolicyVersion::getPolicyValues)
            .map(values -> values.get("rate_bps"))
            .filter(Number.class::isInstance)
            .map(rate -> ((Number) rate).intValue())
            .orElse(null);
    }

    RuleEngineResult revalidate(UUID classroomId, SetupPrecheckInput input) {
        Classroom classroom = classroomRepository.findById(classroomId)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The classroom was not found."
            ));
        return ruleEngineService.evaluate(ruleRequest(classroom, input));
    }

    private AiProposal createProposal(
        Classroom classroom,
        SetupPrecheckInput input,
        RuleEngineResult rules,
        UUID aiRunId,
        Instant now
    ) {
        if (rules.candidates().isEmpty()) {
            return null;
        }
        List<Map<String, Object>> items = rules.candidates().stream()
            .map(this::proposalItem)
            .toList();
        List<Map<String, Object>> ranges = rules.candidates().stream()
            .map(this::rangeItem)
            .toList();
        Map<String, Object> proposed = new LinkedHashMap<>();
        proposed.put("context_type", rules.contextType().name());
        proposed.put("items", items);
        proposed.put("rule_version", rules.ruleVersion());
        proposed.put("rule_input", toMap(input));
        proposed.put(
            "recommendation_action_ids",
            rules.candidates().stream().map(RuleCandidate::actionId).toList()
        );
        SetupProposalTargetSnapshot target = targetSnapshotService.capture(
            classroom,
            input,
            rules
        );
        proposed.put("target_type", target.targetType());
        proposed.put("target_id", target.targetId().toString());
        proposed.put("target_version", target.targetVersion());
        proposed.put("policy_version", target.primaryPolicyVersion());
        proposed.put("draft_snapshot", target.snapshot());
        proposed.put("draft_fingerprint", target.fingerprint());
        if (input.moduleActivation() != null) {
            ClassroomModule module = moduleRepository
                .findByClassroomIdAndModuleCode(
                    classroom.getId(),
                    normalizeModule(input.moduleActivation().moduleCode())
                )
                .orElseThrow();
            proposed.put("module_code", module.getModuleCode());
            proposed.put("module_id", module.getId().toString());
            proposed.put("module_state_version", module.getStateVersion());
            proposed.put("initial_policy_values", initialPolicyValues(input, rules));
        }
        Map<String, Object> allowed = new LinkedHashMap<>();
        allowed.put("items", ranges);
        return AiProposal.setup(
            uuidGenerator.generate(),
            classroom.getId(),
            aiRunId,
            rules.overallSeverity().name(),
            proposed,
            allowed,
            now
        );
    }

    private Map<String, Object> initialPolicyValues(
        SetupPrecheckInput input,
        RuleEngineResult rules
    ) {
        String module = normalizeModule(input.moduleActivation().moduleCode());
        Map<String, Object> values = new LinkedHashMap<>();
        if (Set.of("INCOME_TAX", "DEPOSIT", "LOAN").contains(module)) {
            values.put("rate_bps", rules.candidates().getFirst().proposedValue());
            return values;
        }
        if (!Set.of("ROLE_SALARY", "SALARY").contains(module) || input.salaryPolicy() == null) {
            return values;
        }
        SetupPrecheckInput.SalaryPolicy salary = input.salaryPolicy();
        List<Map<String, Object>> roles = new ArrayList<>();
        for (int index = 0; index < salary.roles().size(); index++) {
            SetupPrecheckInput.SalaryRole role = salary.roles().get(index);
            Map<String, Object> roleValues = new LinkedHashMap<>();
            roleValues.put("role_code", role.roleCode());
            roleValues.put("name", role.name() == null ? role.roleCode() : role.name());
            roleValues.put("weight", role.weight());
            roleValues.put("gross_amount", rules.candidates().get(index).proposedValue());
            roleValues.put("assigned_student_count", value(role.assignedStudentCount(), 0));
            roleValues.put("active", !Boolean.FALSE.equals(role.active()));
            roleValues.put("deductions", Map.of());
            roleValues.put("sort_order", index + 1);
            roles.add(roleValues);
        }
        values.put("roles", roles);
        if (salary.payIntervalDays() != null) {
            values.put("pay_interval_days", salary.payIntervalDays());
        }
        if (salary.firstPayOn() != null) {
            values.put("first_pay_on", salary.firstPayOn().toString());
        }
        return values;
    }

    private Map<String, Object> proposalItem(RuleCandidate candidate) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("action_id", candidate.actionId());
        value.put("target_type", candidate.targetType().name());
        value.put("target_id", candidate.targetId().toString());
        value.put("proposed_value", candidate.proposedValue());
        value.put("unit", candidate.unit().name().equals("BPS") ? "RATE_BPS" : candidate.unit().name());
        value.put("label", candidate.label());
        return value;
    }

    private Map<String, Object> rangeItem(RuleCandidate candidate) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("action_id", candidate.actionId());
        value.put("allowed_min_value", candidate.allowedMinValue());
        value.put("allowed_max_value", candidate.allowedMaxValue());
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        Map<String, Object> result = objectMapper.convertValue(value, Map.class);
        result.values().removeIf(java.util.Objects::isNull);
        return result;
    }

    private static String normalizeModule(String value) {
        if (value == null || value.isBlank()) {
            throw operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "module_code is required.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static void validate(SetupPrecheckOperationCommand command) {
        if (
            command == null || command.classroomId() == null || command.requestId() == null
                || command.correlationId() == null || command.input() == null
                || command.input().contextType() == null
        ) {
            throw operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Check the setup precheck request.");
        }
        SetupPrecheckInput input = command.input();
        // 투자 조건 초안은 투자 모듈 활성화 문맥에서만 쓴다. 다른 문맥에 실려 오면 거절한다.
        boolean contextMatches = switch (input.contextType()) {
            case CLASSROOM_SETUP -> input.moduleActivation() == null
                && input.productDraft() == null
                && input.quizDraft() == null
                && input.investmentDraft() == null;
            case MODULE_ACTIVATION -> input.moduleActivation() != null
                && input.productDraft() == null
                && input.quizDraft() == null;
            case PRODUCT_DRAFT -> input.moduleActivation() == null
                && input.productDraft() != null
                && input.quizDraft() == null
                && input.investmentDraft() == null;
            case QUIZ_SESSION_DRAFT -> input.moduleActivation() == null
                && input.productDraft() == null
                && input.quizDraft() != null
                && input.investmentDraft() == null;
        };
        if (!contextMatches) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_RECOMMENDATION_CONTEXT",
                "The setup context and target draft input do not match."
            );
        }
    }

    private static int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static long value(Long value, long fallback) {
        return value == null ? fallback : value;
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
