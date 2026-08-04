package com.b201.ecovillage.ai.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.b201.ecovillage.ruleengine.domain.ProductKind;
import com.b201.ecovillage.ruleengine.domain.QuizCategory;
import com.b201.ecovillage.ruleengine.domain.QuizMode;
import com.b201.ecovillage.ruleengine.domain.RuleContextType;

public record SetupPrecheckInput(
    RuleContextType contextType,
    Integer activeStudentCount,
    Long systemAmountCap,
    Long nonRewardIssuance,
    Long existingRewardIssuance,
    Long expectedRecovery,
    Long otherPeriodicIssuancePerStudent,
    Integer effectiveTaxBps,
    ModuleActivation moduleActivation,
    SalaryPolicy salaryPolicy,
    ProductDraft productDraft,
    QuizDraft quizDraft
) {
    public record ModuleActivation(
        String moduleCode,
        UUID targetId,
        Long confirmedValue,
        Integer currentDepositRateBps
    ) {
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
        String name,
        BigDecimal weight,
        Long confirmedSalary,
        Integer assignedStudentCount,
        Boolean active
    ) {
    }

    public record ProductDraft(
        UUID targetId,
        ProductKind kind,
        Integer stock,
        Integer expectedDemand,
        BigDecimal expectedPurchaseFrequency,
        Integer vatBps,
        Boolean free,
        Long confirmedPrice
    ) {
    }

    public record QuizDraft(
        UUID targetId,
        QuizMode mode,
        QuizCategory category,
        Integer questionCount,
        Integer expectedParticipants,
        Integer expectedRuns,
        Long confirmedRewardPerPerson
    ) {
    }
}
