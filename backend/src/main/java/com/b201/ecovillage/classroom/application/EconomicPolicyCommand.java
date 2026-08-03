package com.b201.ecovillage.classroom.application;

import java.util.Map;
import java.util.UUID;

public record EconomicPolicyCommand(
    String teacherSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    int priceAdjustmentBps,
    int inflationRateBps,
    int incomeTaxRateBps,
    int vatRateBps,
    int tradeTaxRateBps,
    int depositRateBps,
    int loanRateBps,
    Map<String, Object> stockPolicy,
    Map<String, Object> rewardPolicy,
    Map<String, Object> limits,
    int expectedPolicyVersion
) {
}
