package com.b201.ecovillage.finance.api;

import java.util.UUID;

public record FinancialProductViewResponse(
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
