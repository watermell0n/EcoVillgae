package com.b201.ecovillage.store.api;

public record StorePriceInflationResponse(
    int appliedCount,
    int clampedCount,
    int unchangedCount,
    long stateVersion
) {
}
