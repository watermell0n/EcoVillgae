package com.b201.ecovillage.finance.application;

import java.util.UUID;

public record FinancialProductView(
    UUID resourceId,
    String label,
    String status,
    int version,
    long stateVersion,
    String productType,
    int termUnits,
    int interestRateBps,
    int earlyTerminationRateBps,
    Integer installmentCount,
    Long installmentAmount,
    long minimumAmount,
    long maximumAmount
) {
}
