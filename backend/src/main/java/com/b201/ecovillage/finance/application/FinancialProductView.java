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
    long minimumAmount,
    long maximumAmount
) {
}
