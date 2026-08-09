package com.b201.ecovillage.classroom.application;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.domain.ModuleCatalog;
import com.b201.ecovillage.classroom.domain.SalaryRole;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.classroom.repository.SalaryRoleRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStatus;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.ruleengine.application.ClassroomEconomicMetricsProvider;
import com.b201.ecovillage.ruleengine.application.RuleEngineService;
import com.b201.ecovillage.ruleengine.domain.ClassroomEconomicMetrics;
import com.b201.ecovillage.ruleengine.domain.RuleCandidate;
import com.b201.ecovillage.ruleengine.domain.RuleContextType;
import com.b201.ecovillage.ruleengine.domain.RuleEngineRequest;
import com.b201.ecovillage.ruleengine.domain.RuleEngineResult;
import com.b201.ecovillage.ruleengine.domain.RuleSeverity;
import com.b201.ecovillage.ruleengine.domain.RuleTargetType;

import tools.jackson.databind.ObjectMapper;

@Service
public class ModuleActivationService {

    private static final String ROUTE =
        "/classrooms/{classroom_id}/modules/{module_code}/activation-commands";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);
    /** 시작값이 비율 하나로 끝나는 모듈이다. 규칙 엔진 추천을 그대로 rate_bps에 넣는다. */
    private static final Set<String> RATE_POLICY_MODULES =
        Set.of("INCOME_TAX", "DEPOSIT", "SAVINGS", "LOAN");
    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomModuleRepository moduleRepository;
    private final ClassroomPolicyVersionRepository policyRepository;
    private final SalaryRoleRepository salaryRoleRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final RuleEngineService ruleEngineService;
    private final ClassroomEconomicMetricsProvider metricsProvider;
    private final SetupPrecheckInvalidator precheckInvalidator;
    private final RealtimeEventPublisher eventPublisher;
    private final UuidV7Generator uuidGenerator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ModuleActivationService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ClassroomRepository classroomRepository,
        ClassroomModuleRepository moduleRepository,
        ClassroomPolicyVersionRepository policyRepository,
        SalaryRoleRepository salaryRoleRepository,
        IdempotencyRecordRepository idempotencyRepository,
        RuleEngineService ruleEngineService,
        ClassroomEconomicMetricsProvider metricsProvider,
        SetupPrecheckInvalidator precheckInvalidator,
        RealtimeEventPublisher eventPublisher,
        UuidV7Generator uuidGenerator,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.classroomRepository = classroomRepository;
        this.moduleRepository = moduleRepository;
        this.policyRepository = policyRepository;
        this.salaryRoleRepository = salaryRoleRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.ruleEngineService = ruleEngineService;
        this.metricsProvider = metricsProvider;
        this.precheckInvalidator = precheckInvalidator;
        this.eventPublisher = eventPublisher;
        this.uuidGenerator = uuidGenerator;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public ModuleActivationResult activate(ModuleActivationCommand command) {
        ModuleCatalog.Definition definition = validate(command);
        String moduleCode = definition.code();
        authorizationService.authorize(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.MANAGE_MODULES,
            null
        );
        UserAccount teacher = userRepository
            .findByExternalSubject(command.teacherSubject())
            .orElseThrow(() -> operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A valid teacher session is required."
            ));
        String requestHash = hash(command, moduleCode);
        ModuleActivationResult replay = replay(command, teacher.getId(), requestHash);
        if (replay != null) {
            return replay;
        }

        Classroom classroom = classroomRepository.findById(command.classroomId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The classroom could not be found."
            ));
        if (classroom.getStatus() != ClassroomStatus.ACTIVE) {
            throw operation(
                HttpStatus.CONFLICT,
                "CLASS_NOT_ACTIVE",
                "Modules can be activated only in an active classroom."
            );
        }
        if ("SAVINGS".equals(moduleCode) && classroom.getCourseSize() != CourseSize.SEMESTER) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "MODULE_NOT_AVAILABLE_FOR_COURSE_SIZE",
                "정기적금 모듈은 한 학기 운영에서만 활성화할 수 있습니다."
            );
        }
        ClassroomModule module = moduleRepository
            .findByClassroomIdAndModuleCode(command.classroomId(), moduleCode)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The selected module could not be found in this classroom."
            ));
        if (!ClassroomModule.isActivatable(module.getStatus())) {
            throw operation(
                HttpStatus.CONFLICT,
                "INVALID_STATE_TRANSITION",
                "이미 사용 중인 모듈입니다. 학생 이용만 잠그려면 학급 설정에서 잠금을 사용해 주세요."
            );
        }
        if (command.expectedStateVersion() != module.getStateVersion()) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The module state has changed."
            );
        }
        validatePrerequisites(command.classroomId(), definition);

        String policyArea = definition.policyArea();
        ClassroomPolicyVersion currentPolicy = policyArea == null
            ? null
            : policyRepository
                .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
                    command.classroomId(),
                    policyArea
                )
                .orElse(null);
        Map<String, Object> initialValues = copyValues(command.initialPolicyValues());
        if (definition.initialPolicyRequired() && currentPolicy == null && initialValues.isEmpty()) {
            initialValues = defaultInitialPolicy(classroom, module, moduleCode);
        }
        List<String> investmentModes = investmentModes(moduleCode, initialValues);
        if (
            definition.initialPolicyRequired()
                && currentPolicy == null
                && initialValues.isEmpty()
        ) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INITIAL_POLICY_REQUIRED",
                "An initial policy must be confirmed before this module is activated."
            );
        }
        Map<String, Object> effectiveValues = initialValues.isEmpty() && currentPolicy != null
            ? currentPolicy.getPolicyValues()
            : initialValues;
        if (
            policyArea != null
                && Set.of("INCOME_TAX_RATE", "DEPOSIT_RATE", "SAVINGS_RATE", "LOAN_RATE")
                    .contains(policyArea)
                && rateValue(effectiveValues) == null
        ) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INITIAL_POLICY_REQUIRED",
                "rate_bps must be confirmed before this module is activated."
            );
        }

        RuleEngineResult rules = evaluateRules(
            classroom,
            module,
            moduleCode,
            effectiveValues
        );
        if (rules.overallSeverity() == RuleSeverity.BLOCK) {
            String reasons = rules.checks().stream()
                .filter(check -> check.severity() == RuleSeverity.BLOCK)
                .map(check -> check.reasonCode())
                .distinct()
                .collect(Collectors.joining(", "));
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "POLICY_VIOLATION",
                "The initial policy failed rule validation: " + reasons
            );
        }
        validateRecommendationActions(command.recommendationActionIds(), rules);

        Instant now = clock.instant();
        ClassroomPolicyVersion appliedPolicy = currentPolicy;
        boolean policyChanged = policyArea != null
            && !initialValues.isEmpty()
            && (currentPolicy == null || !currentPolicy.getPolicyValues().equals(initialValues));
        if (policyChanged) {
            int nextVersion = currentPolicy == null ? 1 : currentPolicy.getVersion() + 1;
            if (currentPolicy != null) {
                currentPolicy.supersede(now);
                policyRepository.flush();
            }
            appliedPolicy = ClassroomPolicyVersion.policy(
                uuidGenerator.generate(),
                command.classroomId(),
                policyArea,
                nextVersion,
                initialValues,
                "TEACHER",
                now
            );
            policyRepository.save(appliedPolicy);
            if (isSalaryModule(moduleCode)) {
                replaceSalaryRoles(command.classroomId(), initialValues, now);
            }
        }

        Map<String, Object> moduleConfig = new LinkedHashMap<>();
        moduleConfig.put("rule_version", rules.ruleVersion());
        moduleConfig.put(
            "recommendation_action_ids",
            command.recommendationActionIds() == null
                ? List.of()
                : List.copyOf(command.recommendationActionIds())
        );
        if (appliedPolicy != null) {
            moduleConfig.put("policy_area", appliedPolicy.getPolicyArea());
            moduleConfig.put("policy_version", appliedPolicy.getVersion());
            moduleConfig.put("policy_version_id", appliedPolicy.getId().toString());
        }
        if (investmentModes != null) {
            moduleConfig.put("investment_modes", investmentModes);
            // 단일 값만 읽던 이전 화면과 이미 저장된 학급 설정이 계속 동작하도록 함께 남긴다.
            moduleConfig.put("investment_mode", investmentModes.get(0));
        }
        module.activate(moduleConfig, now);
        classroom.touch(now);
        try {
            policyRepository.flush();
            salaryRoleRepository.flush();
            moduleRepository.saveAndFlush(module);
            classroomRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw operation(
                HttpStatus.CONFLICT,
                "POLICY_VERSION_CONFLICT",
                "The initial policy or module state conflicts with current data."
            );
        }
        precheckInvalidator.invalidate(command.classroomId());

        ModuleActivationResult result = result(module, appliedPolicy, rules.ruleVersion(), false);
        remember(command, moduleCode, teacher.getId(), requestHash, result, now);
        eventPublisher.publish(
            command.classroomId(),
            "CLASSROOM_LIFECYCLE",
            null,
            "classroom.module.changed",
            "CLASSROOM_MODULE",
            module.getId(),
            module.getStateVersion(),
            Map.of(
                "module_id", module.getId().toString(),
                "module_code", module.getModuleCode(),
                "status", module.getStatus(),
                "state_version", module.getStateVersion()
            ),
            true
        );
        return result;
    }

    private RuleEngineResult evaluateRules(
        Classroom classroom,
        ClassroomModule module,
        String moduleCode,
        Map<String, Object> values
    ) {
        Set<String> activeModules = moduleRepository
            .findAllByClassroomIdAndStatusOrderByModuleCode(classroom.getId(), "ACTIVE")
            .stream()
            .map(ClassroomModule::getModuleCode)
            .collect(Collectors.toSet());
        Long confirmedValue = rateValue(values);
        Integer currentDepositRate = currentDepositRate(classroom.getId(), moduleCode);
        ClassroomEconomicMetrics metrics = metricsProvider.of(classroom.getId(), clock.instant());
        RuleEngineRequest.Builder builder = RuleEngineRequest.builder(
            RuleContextType.MODULE_ACTIVATION,
            classroom.getGrade(),
            classroom.getActualStartsOn(),
            classroom.getActualEndsOn()
        )
            .activeStudentCount(classroom.getPlannedStudentCount())
            .currencyName(classroom.getCurrencyName())
            .activeModules(activeModules)
            .metrics(metrics)
            // 지금까지 실제로 오간 금액이다. 아직 운영 전이면 모두 0이라 예산 검증이 꺼진다.
            .budget(metrics.realizedIssuance(), 0L, metrics.realizedRecovery())
            .effectiveTaxBps(currentRateBps(classroom.getId(), "INCOME_TAX_RATE"))
            .moduleActivation(new RuleEngineRequest.ModuleActivation(
                moduleCode,
                module.getId(),
                confirmedValue,
                currentDepositRate
            ));
        // 급여는 금리·세율의 밑바탕이 되는 발행 규모를 정하므로, 급여 모듈을 켜는 중이 아니어도
        // 이미 확정된 급여 정책을 함께 넘긴다. 이것이 없으면 발행액을 알 수 없어 예금·소득세가
        // 학급과 무관한 기본값으로 떨어진다.
        RuleEngineRequest.SalaryPolicy salaryPolicy = isSalaryModule(moduleCode)
            ? salaryPolicy(values)
            : activeSalaryPolicy(classroom.getId());
        if (salaryPolicy != null) {
            builder.salaryPolicy(salaryPolicy);
        }
        return ruleEngineService.evaluate(builder.build());
    }

    /** 이미 확정돼 있는 급여 정책이다. 아직 급여 모듈을 켜지 않았다면 null이다. */
    private RuleEngineRequest.SalaryPolicy activeSalaryPolicy(UUID classroomId) {
        return policyRepository
            .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(classroomId, "SALARY")
            .map(ClassroomPolicyVersion::getPolicyValues)
            .map(this::salaryPolicy)
            .orElse(null);
    }

    /** 확정된 정책의 현재 비율(bps)이다. 정책이 없으면 null이다. */
    private Integer currentRateBps(UUID classroomId, String policyArea) {
        return policyRepository
            .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(classroomId, policyArea)
            .map(ClassroomPolicyVersion::getPolicyValues)
            .map(ModuleActivationService::rateValue)
            .filter(rate -> rate >= 0L && rate <= Integer.MAX_VALUE)
            .map(Long::intValue)
            .orElse(null);
    }

    private void validatePrerequisites(
        UUID classroomId,
        ModuleCatalog.Definition definition
    ) {
        for (String prerequisite : definition.prerequisites()) {
            if (!active(classroomId, prerequisite)) {
                throw prerequisite(prerequisite);
            }
        }
    }

    private boolean active(UUID classroomId, String moduleCode) {
        return moduleRepository.existsByClassroomIdAndModuleCodeAndStatus(
            classroomId,
            moduleCode,
            "ACTIVE"
        );
    }

    private Integer currentDepositRate(UUID classroomId, String moduleCode) {
        if (!"LOAN".equals(moduleCode)) {
            return null;
        }
        ClassroomPolicyVersion deposit = policyRepository
            .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
                classroomId,
                "DEPOSIT_RATE"
            )
            .orElseThrow(() -> prerequisite("DEPOSIT"));
        Long rate = rateValue(deposit.getPolicyValues());
        if (rate == null || rate > Integer.MAX_VALUE) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "POLICY_VIOLATION",
                "The active deposit module has no valid current rate."
            );
        }
        return rate.intValue();
    }

    private RuleEngineRequest.SalaryPolicy salaryPolicy(Map<String, Object> values) {
        List<Map<String, Object>> roles = roleMaps(values);
        List<RuleEngineRequest.SalaryRole> ruleRoles = new ArrayList<>();
        for (Map<String, Object> role : roles) {
            String code = text(role, "role_code");
            Long salary = number(role.get("gross_amount"));
            BigDecimal weight = decimal(role.get("weight"));
            if (weight == null && salary != null) {
                weight = BigDecimal.valueOf(salary)
                    .divide(BigDecimal.valueOf(1_000L));
            }
            ruleRoles.add(new RuleEngineRequest.SalaryRole(
                code,
                weight,
                salary,
                intValue(role.get("assigned_student_count"), 0),
                !Boolean.FALSE.equals(role.get("active"))
            ));
        }
        return new RuleEngineRequest.SalaryPolicy(
            ruleRoles,
            integer(values.get("pay_interval_days")),
            localDate(values.get("first_pay_on")),
            number(values.get("default_role_salary"))
        );
    }

    private void replaceSalaryRoles(
        UUID classroomId,
        Map<String, Object> values,
        Instant now
    ) {
        List<Map<String, Object>> roles = roleMaps(values);
        if (roles.isEmpty()) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INITIAL_POLICY_REQUIRED",
                "At least one salary role is required."
            );
        }
        List<SalaryRole> existing = salaryRoleRepository
            .findAllByClassroomIdOrderBySortOrder(classroomId);
        existing.forEach(role -> role.deactivate(now));
        Map<String, SalaryRole> byCode = existing.stream()
            .collect(Collectors.toMap(SalaryRole::getRoleCode, role -> role));
        for (int index = 0; index < roles.size(); index++) {
            Map<String, Object> valuesByRole = roles.get(index);
            String code = text(valuesByRole, "role_code");
            String name = text(valuesByRole, "name");
            Long grossAmount = number(valuesByRole.get("gross_amount"));
            if (
                code == null || code.isBlank() || name == null || name.isBlank()
                    || grossAmount == null || grossAmount < 0
            ) {
                throw operation(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Each salary role requires role_code, name, and gross_amount."
                );
            }
            Map<String, Object> deductions = objectMap(valuesByRole.get("deductions"));
            SalaryRole role = byCode.get(code.toUpperCase(Locale.ROOT));
            if (role == null) {
                role = SalaryRole.create(
                    uuidGenerator.generate(),
                    classroomId,
                    code.toUpperCase(Locale.ROOT),
                    name.trim(),
                    grossAmount,
                    deductions,
                    intValue(valuesByRole.get("sort_order"), index + 1),
                    now
                );
                byCode.put(role.getRoleCode(), role);
            } else {
                role.update(
                    name.trim(),
                    grossAmount,
                    deductions,
                    intValue(valuesByRole.get("sort_order"), index + 1),
                    now
                );
            }
        }
        salaryRoleRepository.saveAll(byCode.values());
    }

    private ModuleActivationResult replay(
        ModuleActivationCommand command,
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
        Map<String, Object> body = record.getResponseBody();
        return new ModuleActivationResult(
            body.get("module_code").toString(),
            body.get("status").toString(),
            ((Number) body.get("state_version")).longValue(),
            nullableText(body.get("policy_area")),
            nullableInteger(body.get("policy_version")),
            nullableUuid(body.get("policy_version_id")),
            body.get("rule_version").toString(),
            true
        );
    }

    private void remember(
        ModuleActivationCommand command,
        String moduleCode,
        UUID actorId,
        String requestHash,
        ModuleActivationResult result,
        Instant now
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("module_code", result.moduleCode());
        body.put("status", result.status());
        body.put("state_version", result.stateVersion());
        body.put("policy_area", result.policyArea() == null ? "" : result.policyArea());
        body.put("policy_version", result.policyVersion() == null ? -1 : result.policyVersion());
        body.put(
            "policy_version_id",
            result.policyVersionId() == null ? "" : result.policyVersionId().toString()
        );
        body.put("rule_version", result.ruleVersion());
        idempotencyRepository.save(IdempotencyRecord.completed(
            uuidGenerator.generate(),
            command.classroomId(),
            actorId,
            ROUTE,
            command.requestId().toString(),
            requestHash,
            HttpStatus.OK.value(),
            body,
            null,
            now.plus(IDEMPOTENCY_TTL),
            now
        ));
    }

    private String hash(ModuleActivationCommand command, String moduleCode) {
        try {
            Map<String, Object> hashInput = new TreeMap<>();
            hashInput.put("classroom_id", command.classroomId().toString());
            hashInput.put("module_code", moduleCode);
            hashInput.put("expected_state_version", command.expectedStateVersion());
            hashInput.put("confirm_prerequisites", command.confirmPrerequisites());
            hashInput.put("initial_policy_values", canonical(command.initialPolicyValues()));
            hashInput.put(
                "recommendation_action_ids",
                command.recommendationActionIds() == null
                    ? List.of()
                    : command.recommendationActionIds()
            );
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                objectMapper.writeValueAsBytes(hashInput)
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash the module activation command.", exception);
        }
    }

    private static Object canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, nested) -> sorted.put(String.valueOf(key), canonical(nested)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(ModuleActivationService::canonical).toList();
        }
        return value;
    }

    private static ModuleCatalog.Definition validate(ModuleActivationCommand command) {
        if (
            command == null || command.classroomId() == null || command.requestId() == null
                || command.moduleCode() == null || command.moduleCode().isBlank()
                || command.expectedStateVersion() == null
        ) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Check the module activation command values."
            );
        }
        if (!command.confirmPrerequisites()) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "PREREQUISITE_CONFIRMATION_REQUIRED",
                "Module prerequisites must be confirmed before activation."
            );
        }
        return ModuleCatalog.find(command.moduleCode()).orElseThrow(() -> operation(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "UNSUPPORTED_MODULE_CODE",
            "The module code is not selectable: " + command.moduleCode()
        ));
    }

    private static void validateRecommendationActions(
        List<String> actionIds,
        RuleEngineResult result
    ) {
        if (actionIds == null || actionIds.isEmpty()) {
            return;
        }
        Set<String> current = result.candidates().stream()
            .map(candidate -> candidate.actionId())
            .collect(Collectors.toSet());
        if (!current.containsAll(actionIds)) {
            throw operation(
                HttpStatus.CONFLICT,
                "POLICY_VERSION_CONFLICT",
                "A referenced recommendation is no longer current."
            );
        }
    }

    private static ModuleActivationResult result(
        ClassroomModule module,
        ClassroomPolicyVersion policy,
        String ruleVersion,
        boolean replayed
    ) {
        return new ModuleActivationResult(
            module.getModuleCode(),
            module.getStatus(),
            module.getStateVersion(),
            policy == null ? null : policy.getPolicyArea(),
            policy == null ? null : policy.getVersion(),
            policy == null ? null : policy.getId(),
            ruleVersion,
            replayed
        );
    }

    /**
     * 학급은 단기 투자와 장기 투자를 함께 운영할 수 있으므로 방식은 목록으로 받는다.
     *
     * <p>{@code investment_modes} 목록을 쓰고, 목록이 없으면 이전 화면이 보내던
     * {@code investment_mode} 단일 값을 한 개짜리 목록으로 읽는다.
     */
    static List<String> investmentModes(
        String moduleCode,
        Map<String, Object> initialValues
    ) {
        if (!"INVESTMENT".equals(moduleCode)) {
            return null;
        }
        Collection<?> raws = initialValues.get("investment_modes") instanceof Collection<?> values
            ? values
            : Set.of(initialValues.getOrDefault("investment_mode", "LONG_TERM"));
        LinkedHashSet<String> modes = new LinkedHashSet<>();
        for (Object raw : raws) {
            String normalized = raw == null
                ? ""
                : raw.toString().trim().toUpperCase(Locale.ROOT);
            if (!Set.of("SHORT_TERM", "LONG_TERM").contains(normalized)) {
                throw operation(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_INVESTMENT_MODE",
                    "investment_modes must contain only SHORT_TERM or LONG_TERM."
                );
            }
            modes.add(normalized);
        }
        if (modes.isEmpty()) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "INVALID_INVESTMENT_MODE",
                "At least one investment mode must be selected."
            );
        }
        return List.copyOf(modes);
    }

    private static boolean isSalaryModule(String moduleCode) {
        return "ROLE_SALARY".equals(moduleCode);
    }

    private static ClassroomOperationException prerequisite(String moduleCode) {
        return operation(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "PREREQUISITE_MODULE_REQUIRED",
            moduleCode + " must be active before this module can be activated."
        );
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }

    private static Map<String, Object> copyValues(Map<String, Object> values) {
        return values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }

    /**
     * 교사가 학급 설정 화면에서 모듈을 그냥 켰을 때 쓸 시작값이다.
     *
     * <p>예전에는 학년과 운영 규모만 보고 고정된 숫자를 넣었다. 그래서 학생 수도 발행액도 다른
     * 학급이 모두 같은 금리로 시작했다. 지금은 뼈대만 만들어 규칙 엔진에 한 번 물어보고, 이
     * 학급의 실제 규모에서 나온 값을 시작값으로 쓴다. 교사는 나중에 정책 화면에서 바꿀 수 있다.
     */
    private Map<String, Object> defaultInitialPolicy(
        Classroom classroom,
        ClassroomModule module,
        String moduleCode
    ) {
        if (!isSalaryModule(moduleCode) && !RATE_POLICY_MODULES.contains(moduleCode)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> draft = defaultPolicySkeleton(classroom, moduleCode);
        RuleEngineResult rules = evaluateRules(classroom, module, moduleCode, draft);
        return applyRecommendations(moduleCode, draft, rules);
    }

    /**
     * 규칙 엔진에 물어보기 위한 뼈대다. 숫자는 자리만 채워 두고, 실제 값은 추천으로 덮어쓴다.
     * 규칙 엔진이 후보를 만들려면 급여는 역할 목록이, 금리는 대상 모듈이 있어야 하기 때문이다.
     */
    private static Map<String, Object> defaultPolicySkeleton(
        Classroom classroom,
        String moduleCode
    ) {
        return switch (moduleCode) {
            case "ROLE_SALARY" -> new LinkedHashMap<>(Map.of(
                "roles", List.of(new LinkedHashMap<>(Map.of(
                    "role_code", "DEFAULT_ROLE",
                    "name", "기본 역할",
                    "gross_amount", 1_000L,
                    "weight", 1L,
                    "assigned_student_count", classroom.getPlannedStudentCount(),
                    "deductions", Map.of(),
                    "sort_order", 1
                ))),
                "pay_interval_days", payIntervalDays(classroom),
                "first_pay_on", classroom.getActualStartsOn() == null
                    ? LocalDate.now().toString()
                    : classroom.getActualStartsOn().toString(),
                "default_role_salary", 1_000L
            ));
            // 비율 모듈은 채울 값이 하나뿐이다. 자리표시자를 넣으면 규칙 엔진이 그 숫자를 교사가
            // 정한 값으로 오해해 범위 위반으로 막으므로, 비워 둔 채로 추천만 받는다.
            default -> new LinkedHashMap<>();
        };
    }

    /**
     * 운영 기간 동안 급여가 4~8번쯤 나가도록 지급 주기를 잡는다. 한두 번만 나가면 학생이 벌고
     * 쓰는 흐름을 겪지 못하고, 너무 잦으면 한 번의 급여가 너무 작아진다.
     */
    private static int payIntervalDays(Classroom classroom) {
        LocalDate from = classroom.getActualStartsOn();
        LocalDate to = classroom.getActualEndsOn();
        if (from == null || to == null || to.isBefore(from)) {
            return 7;
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1L;
        long interval = Math.round(days / 6.0d);
        return (int) Math.min(14L, Math.max(1L, interval));
    }

    /**
     * 규칙 엔진이 낸 추천값을 뼈대에 채워 넣는다. 후보가 없으면(예: 선행 조건이 아직 없어
     * BLOCK인 경우) 뼈대를 그대로 두어 이후 검증이 평소대로 안내하게 한다.
     */
    private static Map<String, Object> applyRecommendations(
        String moduleCode,
        Map<String, Object> draft,
        RuleEngineResult rules
    ) {
        if (rules.candidates().isEmpty()) {
            return draft;
        }
        if (isSalaryModule(moduleCode)) {
            List<Map<String, Object>> roles = roleMaps(draft);
            List<RuleCandidate> salaryCandidates = rules.candidates().stream()
                .filter(candidate -> candidate.targetType() == RuleTargetType.ROLE_SALARY)
                .toList();
            for (int index = 0; index < roles.size() && index < salaryCandidates.size(); index++) {
                roles.get(index).put("gross_amount", salaryCandidates.get(index).proposedValue());
            }
            if (!salaryCandidates.isEmpty()) {
                draft.put("roles", roles);
                draft.put("default_role_salary", salaryCandidates.getFirst().proposedValue());
            }
            return draft;
        }
        RuleTargetType expected = switch (moduleCode) {
            case "INCOME_TAX" -> RuleTargetType.INCOME_TAX_RATE;
            case "DEPOSIT" -> RuleTargetType.DEPOSIT_RATE;
            case "SAVINGS" -> RuleTargetType.SAVINGS_RATE;
            case "LOAN" -> RuleTargetType.LOAN_RATE;
            default -> null;
        };
        if (expected == null) {
            return draft;
        }
        rules.candidates().stream()
            .filter(candidate -> candidate.targetType() == expected)
            .findFirst()
            .ifPresent(candidate -> draft.put("rate_bps", candidate.proposedValue()));
        return draft;
    }

    private static List<Map<String, Object>> roleMaps(Map<String, Object> values) {
        Object raw = values.get("roles");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw operation(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Salary policy roles must be objects."
                );
            }
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((key, value) -> converted.put(String.valueOf(key), value));
            result.add(converted);
        }
        return result;
    }

    private static Map<String, Object> objectMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        map.forEach((key, value) -> converted.put(String.valueOf(key), value));
        return converted;
    }

    private static String text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString().trim();
    }

    private static Long rateValue(Map<String, Object> values) {
        return number(values.get("rate_bps"));
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static int intValue(Object value, int defaultValue) {
        Integer result = integer(value);
        return result == null ? defaultValue : result;
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static LocalDate localDate(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.toString());
        } catch (Exception exception) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "first_pay_on must be an ISO date."
            );
        }
    }

    private static String nullableText(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private static Integer nullableInteger(Object value) {
        if (!(value instanceof Number number) || number.intValue() < 0) {
            return null;
        }
        return number.intValue();
    }

    private static UUID nullableUuid(Object value) {
        String text = nullableText(value);
        return text == null ? null : UUID.fromString(text);
    }
}
