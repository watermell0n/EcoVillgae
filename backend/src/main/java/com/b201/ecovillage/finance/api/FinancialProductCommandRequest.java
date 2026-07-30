package com.b201.ecovillage.finance.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.b201.ecovillage.finance.application.FinancialProductCommandType;
import com.b201.ecovillage.finance.domain.FinancialProductType;

public record FinancialProductCommandRequest(
    @Min(0) Long expectedStateVersion,
    @Size(max = 120) String screenId,
    @Size(max = 120) String uiActionId,
    Instant clientOccurredAt,
    @NotNull FinancialProductCommandType command,
    UUID productId,
    @NotNull FinancialProductType productType,
    @NotBlank @Size(max = 120) String name,
    @Min(1) int termUnits,
    @Min(0) @Max(10_000) int interestRateBps,
    @Min(0) @Max(10_000) int earlyTerminationRateBps,
    @Min(2) @Max(12) Integer installmentCount,
    @Min(1) Long installmentAmount,
    @Min(1) long minimumAmount,
    @Min(1) long maximumAmount,
    @Min(0) Integer expectedProductVersion
) {
}
