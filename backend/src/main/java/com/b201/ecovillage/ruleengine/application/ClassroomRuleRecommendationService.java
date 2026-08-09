package com.b201.ecovillage.ruleengine.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.ruleengine.domain.ClassroomEconomicMetrics;
import com.b201.ecovillage.ruleengine.domain.RuleCandidate;
import com.b201.ecovillage.ruleengine.domain.RuleContextType;
import com.b201.ecovillage.ruleengine.domain.RuleEngineRequest;
import com.b201.ecovillage.ruleengine.domain.RuleTargetType;

/**
 * 지금 학급 상태에서 각 금리·세율이 얼마여야 하는지를 규칙 엔진에 물어본다.
 *
 * <p>운영 중 AI 모니터링은 "지금 값에서 조금 올리거나 내리라"만 제안할 수 있었다. 어느 쪽이 옳은
 * 방향인지는 알지 못해 결과가 학급 사정과 무관했다. 이 서비스가 학급의 실제 발행·회수·연체를
 * 넣어 목표값을 계산해 주면, 모니터링은 그 방향으로만 조정안을 낼 수 있다.
 *
 * <p>학급이나 모듈을 찾지 못하거나 규칙이 후보를 내지 못하면 빈 값을 돌려준다. 목표를 모른다고
 * 모니터링이 멈추면 안 되기 때문이다.
 */
@Service
public class ClassroomRuleRecommendationService {

    private static final Map<String, RuleTargetType> RATE_TARGETS = Map.of(
        "DEPOSIT", RuleTargetType.DEPOSIT_RATE,
        "SAVINGS", RuleTargetType.SAVINGS_RATE,
        "LOAN", RuleTargetType.LOAN_RATE,
        "INCOME_TAX", RuleTargetType.INCOME_TAX_RATE
    );

    private final ClassroomRepository classroomRepository;
    private final ClassroomModuleRepository moduleRepository;
    private final ClassroomPolicyVersionRepository policyRepository;
    private final ClassroomEconomicMetricsProvider metricsProvider;
    private final RuleEngineService ruleEngineService;

    public ClassroomRuleRecommendationService(
        ClassroomRepository classroomRepository,
        ClassroomModuleRepository moduleRepository,
        ClassroomPolicyVersionRepository policyRepository,
        ClassroomEconomicMetricsProvider metricsProvider,
        RuleEngineService ruleEngineService
    ) {
        this.classroomRepository = classroomRepository;
        this.moduleRepository = moduleRepository;
        this.policyRepository = policyRepository;
        this.metricsProvider = metricsProvider;
        this.ruleEngineService = ruleEngineService;
    }

    /** 모듈 하나의 권장 비율(bps)이다. 계산할 수 없으면 빈 값이다. */
    public Optional<Long> recommendedRateBps(
        UUID classroomId,
        String moduleCode,
        Instant asOf
    ) {
        RuleTargetType targetType = RATE_TARGETS.get(normalize(moduleCode));
        if (targetType == null) {
            return Optional.empty();
        }
        return classroomRepository.findById(classroomId)
            .flatMap(classroom -> evaluate(classroom, normalize(moduleCode), targetType, asOf));
    }

    private Optional<Long> evaluate(
        Classroom classroom,
        String moduleCode,
        RuleTargetType targetType,
        Instant asOf
    ) {
        Optional<ClassroomModule> module = moduleRepository
            .findByClassroomIdAndModuleCode(classroom.getId(), moduleCode);
        if (module.isEmpty()) {
            return Optional.empty();
        }
        Integer depositRate = "LOAN".equals(moduleCode)
            ? currentRateBps(classroom.getId(), "DEPOSIT_RATE")
            : null;
        if ("LOAN".equals(moduleCode) && depositRate == null) {
            return Optional.empty();
        }
        ClassroomEconomicMetrics metrics = metricsProvider.of(classroom.getId(), asOf);
        Set<String> activeModules = moduleRepository
            .findAllByClassroomIdAndStatusOrderByModuleCode(classroom.getId(), "ACTIVE")
            .stream()
            .map(ClassroomModule::getModuleCode)
            .collect(Collectors.toSet());
        RuleEngineRequest.Builder builder = RuleEngineRequest.builder(
            RuleContextType.MODULE_ACTIVATION,
            classroom.getGrade(),
            classroom.getActualStartsOn(),
            classroom.getActualEndsOn()
        )
            .activeStudentCount(effectiveStudentCount(classroom, metrics))
            .currencyName(classroom.getCurrencyName())
            .activeModules(activeModules)
            .metrics(metrics)
            .budget(metrics.realizedIssuance(), 0L, metrics.realizedRecovery())
            .effectiveTaxBps(currentRateBps(classroom.getId(), "INCOME_TAX_RATE"))
            .moduleActivation(new RuleEngineRequest.ModuleActivation(
                moduleCode,
                module.get().getId(),
                // 지금 값은 넣지 않는다. "지금 값이 맞는가"가 아니라 "얼마여야 하는가"를 묻는다.
                null,
                depositRate
            ));
        salaryPolicy(classroom.getId()).ifPresent(builder::salaryPolicy);
        return ruleEngineService.evaluate(builder.build()).candidates().stream()
            .filter(candidate -> candidate.targetType() == targetType)
            .findFirst()
            .map(RuleCandidate::proposedValue);
    }

    /** 실제로 지갑을 가진 학생이 있으면 그 수를, 아직 없으면 계획 인원을 쓴다. */
    private static int effectiveStudentCount(
        Classroom classroom,
        ClassroomEconomicMetrics metrics
    ) {
        return metrics.walletCount() > 0
            ? metrics.walletCount()
            : classroom.getPlannedStudentCount();
    }

    private Integer currentRateBps(UUID classroomId, String policyArea) {
        return policyRepository
            .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(classroomId, policyArea)
            .map(ClassroomPolicyVersion::getPolicyValues)
            .map(values -> values.get("rate_bps"))
            .filter(Number.class::isInstance)
            .map(rate -> ((Number) rate).intValue())
            .orElse(null);
    }

    private Optional<RuleEngineRequest.SalaryPolicy> salaryPolicy(UUID classroomId) {
        return policyRepository
            .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(classroomId, "SALARY")
            .map(ClassroomPolicyVersion::getPolicyValues)
            .map(ClassroomRuleRecommendationService::toSalaryPolicy)
            .filter(policy -> !policy.roles().isEmpty());
    }

    private static RuleEngineRequest.SalaryPolicy toSalaryPolicy(Map<String, Object> values) {
        List<RuleEngineRequest.SalaryRole> roles = new ArrayList<>();
        if (values.get("roles") instanceof List<?> rawRoles) {
            for (Object raw : rawRoles) {
                if (!(raw instanceof Map<?, ?> role)) {
                    continue;
                }
                Long gross = longValue(role.get("gross_amount"));
                BigDecimal weight = decimalValue(role.get("weight"));
                if (weight == null && gross != null) {
                    weight = BigDecimal.valueOf(gross)
                        .divide(BigDecimal.valueOf(1_000L), 4, RoundingMode.HALF_UP);
                }
                Long assigned = longValue(role.get("assigned_student_count"));
                roles.add(new RuleEngineRequest.SalaryRole(
                    role.get("role_code") == null ? null : role.get("role_code").toString(),
                    weight,
                    gross,
                    assigned == null ? 0 : assigned.intValue(),
                    !Boolean.FALSE.equals(role.get("active"))
                ));
            }
        }
        Long interval = longValue(values.get("pay_interval_days"));
        return new RuleEngineRequest.SalaryPolicy(
            roles,
            interval == null ? null : interval.intValue(),
            null,
            longValue(values.get("default_role_salary"))
        );
    }

    private static Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return value instanceof Number number ? new BigDecimal(number.toString()) : null;
    }

    private static String normalize(String moduleCode) {
        if (moduleCode == null) {
            return "";
        }
        String normalized = moduleCode.trim().toUpperCase(Locale.ROOT);
        return "SALARY".equals(normalized) ? "ROLE_SALARY" : normalized;
    }
}
