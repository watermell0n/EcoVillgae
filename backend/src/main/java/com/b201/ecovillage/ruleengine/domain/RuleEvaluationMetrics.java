package com.b201.ecovillage.ruleengine.domain;

import java.math.BigDecimal;

public record RuleEvaluationMetrics(
    long referenceSalary,
    long netReferenceSalary,
    PaymentSchedule paymentSchedule,
    long nonRewardIssuance,
    long existingRewardIssuance,
    long candidateRewardIssuance,
    BigDecimal rewardIssuanceShare,
    long expectedRecovery,
    BigDecimal recoveryRatio
) {
}
