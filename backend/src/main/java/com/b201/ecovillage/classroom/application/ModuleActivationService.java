package com.b201.ecovillage.classroom.application;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
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
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.ruleengine.application.RuleEngineService;
import com.b201.ecovillage.ruleengine.domain.RuleContextType;
import com.b201.ecovillage.ruleengine.domain.RuleEngineRequest;
import com.b201.ecovillage.ruleengine.domain.RuleEngineResult;
import com.b201.ecovillage.ruleengine.domain.RuleSeverity;

import tools.jackson.databind.ObjectMapper;

@Service
public class ModuleActivationService {

    private static final String ROUTE =
        "/classrooms/{classroom_id}/modules/{module_code}/activation-commands";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);
    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomModuleRepository moduleRepository;
    private final ClassroomPolicyVersionRepository policyRepository;
    private final SalaryRoleRepository salaryRoleRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final RuleEngineService ruleEngineService;
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
        ClassroomModule module = moduleRepository
            .findByClassroomIdAndModuleCode(command.classroomId(), moduleCode)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The selected module could not be found in this classroom."
            ));
        if (!Set.of("DISABLED", "PENDING_CONFIGURATION").contains(module.getStatus())) {
            throw operation(
                HttpStatus.CONFLICT,
                "INVALID_STATE_TRANSITION",
                "Only a disabled or pending module can be activated."
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
        String investmentMode = investmentMode(moduleCode, initialValues);
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
                && Set.of("INCOME_TAX_RATE", "DEPOSIT_RATE", "LOAN_RATE").contains(policyArea)
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
        if (investmentMode != null) {
            moduleConfig.put("investment_mode", investmentMode);
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
        RuleEngineRequest.Builder builder = RuleEngineRequest.builder(
            RuleContextType.MODULE_ACTIVATION,
            classroom.getGrade(),
            classroom.getActualStartsOn(),
            classroom.getActualEndsOn()
        )
            .activeStudentCount(classroom.getPlannedStudentCount())
            .currencyName(classroom.getCurrencyName())
            .activeModules(activeModules)
            .moduleActivation(new RuleEngineRequest.ModuleActivation(
                moduleCode,
                module.getId(),
                confirmedValue,
                currentDepositRate
            ));
        if (isSalaryModule(moduleCode)) {
            builder.salaryPolicy(salaryPolicy(values));
        }
        return ruleEngineService.evaluate(builder.build());
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

    private static String investmentMode(
        String moduleCode,
        Map<String, Object> initialValues
    ) {
        if (!"INVESTMENT".equals(moduleCode)) {
            return null;
        }
        Object raw = initialValues.getOrDefault("investment_mode", "LONG_TERM");
        String normalized = raw == null
            ? ""
            : raw.toString().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("SHORT_TERM", "LONG_TERM").contains(normalized)) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "INVALID_INVESTMENT_MODE",
                "investment_mode must be SHORT_TERM or LONG_TERM."
            );
        }
        return normalized;
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
