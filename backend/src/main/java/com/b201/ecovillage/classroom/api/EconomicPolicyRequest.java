package com.b201.ecovillage.classroom.api;

import java.time.Instant;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record EconomicPolicyRequest(
    @Min(0) Long expectedStateVersion,
    @Pattern(regexp = "^SCR-[A-Z]+-\\d{3}$") String screenId,
    @Pattern(regexp = "^UIA-[A-Z]+-\\d{3}$") String uiActionId,
    Instant clientOccurredAt,
    @NotNull @Min(-10000) @Max(100000) Integer priceAdjustmentBps,
    @NotNull @Min(-10000) @Max(100000) Integer inflationRateBps,
    @NotNull @Min(0) @Max(10000) Integer incomeTaxRateBps,
    @NotNull @Min(0) @Max(10000) Integer vatRateBps,
    @NotNull @Min(0) @Max(10000) Integer tradeTaxRateBps,
    @NotNull @Min(0) @Max(10000) Integer depositRateBps,
    @NotNull @Min(0) @Max(10000) Integer loanRateBps,
    @NotNull Map<String, Object> stockPolicy,
    @NotNull Map<String, Object> rewardPolicy,
    @NotNull Map<String, Object> limits,
    @NotNull @Min(0) Integer expectedPolicyVersion
) {
}
