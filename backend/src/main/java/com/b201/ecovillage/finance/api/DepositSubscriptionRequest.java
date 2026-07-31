package com.b201.ecovillage.finance.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DepositSubscriptionRequest(
    @Min(0) Long expectedStateVersion,
    @Size(max = 120) String screenId,
    @Size(max = 120) String uiActionId,
    Instant clientOccurredAt,
    @NotNull UUID productId,
    @Min(1) long principal,
    @Min(1) int expectedProductVersion
) {
}
