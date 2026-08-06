package com.b201.ecovillage.ruleengine.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public record RuleEngineRequest(
    RuleContextType contextType,
    int grade,
    LocalDate operationStartsOn,
    LocalDate operationEndsOn,
    int activeStudentCount,
    String currencyName,
    Set<String> activeModules,
    long systemAmountCap,
    long nonRewardIssuance,
    long existingRewardIssuance,
    long expectedRecovery,
    Long otherPeriodicIssuancePerStudent,
    Integer effectiveTaxBps,
    ModuleActivation moduleActivation,
    SalaryPolicy salaryPolicy,
    ProductDraft productDraft,
    QuizDraft quizDraft,
    InvestmentDraft investmentDraft
) {
    public RuleEngineRequest {
        TreeSet<String> normalizedModules = new TreeSet<>();
        if (activeModules != null) {
            activeModules.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .forEach(normalizedModules::add);
        }
        activeModules = Set.copyOf(normalizedModules);
    }

    public static Builder builder(
        RuleContextType contextType,
        int grade,
        LocalDate operationStartsOn,
        LocalDate operationEndsOn
    ) {
        return new Builder(contextType, grade, operationStartsOn, operationEndsOn);
    }

    public record ModuleActivation(
        String moduleCode,
        UUID targetId,
        Long confirmedValue,
        Integer currentDepositRateBps
    ) {
        public ModuleActivation {
            if (moduleCode != null) {
                moduleCode = moduleCode.trim().toUpperCase(Locale.ROOT);
            }
        }
    }

    public record SalaryPolicy(
        List<SalaryRole> roles,
        Integer payIntervalDays,
        LocalDate firstPayOn,
        Long defaultRoleSalary
    ) {
        public SalaryPolicy {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }

    public record SalaryRole(
        String roleCode,
        BigDecimal weight,
        Long confirmedSalary,
        int assignedStudentCount,
        boolean active
    ) {
    }

    public record ProductDraft(
        UUID targetId,
        ProductKind kind,
        int stock,
        int expectedDemand,
        BigDecimal expectedPurchaseFrequency,
        int vatBps,
        boolean free,
        Long confirmedPrice
    ) {
    }

    public record QuizDraft(
        UUID targetId,
        QuizMode mode,
        QuizCategory category,
        int questionCount,
        Integer expectedParticipants,
        Integer expectedRuns,
        Long confirmedRewardPerPerson
    ) {
    }

    /**
     * 투자 조건 추천에 쓰는 학급 지표와 교사 확정값이다.
     *
     * <p>금액은 학급 화폐 단위이며, 잔액은 지금 학생 지갑에 남아 있는 돈이다. 아직 아무도
     * 돈을 받지 않은 학급이면 잔액이 0이므로 화폐 발행액을 대신 본다.
     */
    public record InvestmentDraft(
        UUID targetId,
        String investmentMode,
        long moneyIssuance,
        long averageStudentBalance,
        long medianStudentBalance,
        List<InvestmentAsset> assets
    ) {
        public InvestmentDraft {
            if (investmentMode != null) {
                investmentMode = investmentMode.trim().toUpperCase(Locale.ROOT);
            }
            assets = assets == null ? List.of() : List.copyOf(assets);
        }
    }

    /**
     * 종목 하나다. priceWeight는 종목 사이의 가격 차이만, riskWeight는 위험 차이만 정한다.
     * confirmedBandBps는 교사가 이미 정해 둔 하루 변동 범위(±bps)다.
     */
    public record InvestmentAsset(
        String symbol,
        String name,
        BigDecimal priceWeight,
        BigDecimal riskWeight,
        Long confirmedPrice,
        Long confirmedBandBps
    ) {
    }

    public static final class Builder {
        private final RuleContextType contextType;
        private final int grade;
        private final LocalDate operationStartsOn;
        private final LocalDate operationEndsOn;
        private int activeStudentCount;
        private String currencyName = "미소";
        private Set<String> activeModules = Set.of();
        private long systemAmountCap = 1_000_000L;
        private long nonRewardIssuance;
        private long existingRewardIssuance;
        private long expectedRecovery;
        private Long otherPeriodicIssuancePerStudent;
        private Integer effectiveTaxBps;
        private ModuleActivation moduleActivation;
        private SalaryPolicy salaryPolicy;
        private ProductDraft productDraft;
        private QuizDraft quizDraft;
        private InvestmentDraft investmentDraft;

        private Builder(
            RuleContextType contextType,
            int grade,
            LocalDate operationStartsOn,
            LocalDate operationEndsOn
        ) {
            this.contextType = contextType;
            this.grade = grade;
            this.operationStartsOn = operationStartsOn;
            this.operationEndsOn = operationEndsOn;
        }

        public Builder activeStudentCount(int value) {
            activeStudentCount = value;
            return this;
        }

        public Builder currencyName(String value) {
            currencyName = value;
            return this;
        }

        public Builder activeModules(Set<String> value) {
            activeModules = value;
            return this;
        }

        public Builder systemAmountCap(long value) {
            systemAmountCap = value;
            return this;
        }

        public Builder budget(long nonReward, long existingReward, long recovery) {
            nonRewardIssuance = nonReward;
            existingRewardIssuance = existingReward;
            expectedRecovery = recovery;
            return this;
        }

        public Builder otherPeriodicIssuancePerStudent(Long value) {
            otherPeriodicIssuancePerStudent = value;
            return this;
        }

        public Builder effectiveTaxBps(Integer value) {
            effectiveTaxBps = value;
            return this;
        }

        public Builder moduleActivation(ModuleActivation value) {
            moduleActivation = value;
            return this;
        }

        public Builder salaryPolicy(SalaryPolicy value) {
            salaryPolicy = value;
            return this;
        }

        public Builder productDraft(ProductDraft value) {
            productDraft = value;
            return this;
        }

        public Builder quizDraft(QuizDraft value) {
            quizDraft = value;
            return this;
        }

        public Builder investmentDraft(InvestmentDraft value) {
            investmentDraft = value;
            return this;
        }

        public RuleEngineRequest build() {
            return new RuleEngineRequest(
                contextType,
                grade,
                operationStartsOn,
                operationEndsOn,
                activeStudentCount,
                currencyName,
                activeModules,
                systemAmountCap,
                nonRewardIssuance,
                existingRewardIssuance,
                expectedRecovery,
                otherPeriodicIssuancePerStudent,
                effectiveTaxBps,
                moduleActivation,
                salaryPolicy,
                productDraft,
                quizDraft,
                investmentDraft
            );
        }
    }
}
