package com.b201.ecovillage.finance.api;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record LoanPolicyRequest(
    Long expectedStateVersion,
    String screenId,
    String uiActionId,
    Instant clientOccurredAt,
    @Min(1) long maximumPrincipal,
    @Min(0) @Max(2_000) int interestRateBps,
    @Min(7) @Max(84) int termUnits,
    @Min(1) @Max(12) int installmentCount,
    @Min(0) @Max(7) int graceUnits,
    @Min(0) @Max(100) int onTimeCredit,
    @Min(-100) @Max(0) int overdueCredit,
    @Min(0) @Max(100) int repaidCredit,
    @Min(0) Integer expectedPolicyVersion
) { }
