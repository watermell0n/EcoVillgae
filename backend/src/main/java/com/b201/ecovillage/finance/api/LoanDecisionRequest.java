package com.b201.ecovillage.finance.api;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

public record LoanDecisionRequest(
    Long expectedStateVersion,
    String screenId,
    String uiActionId,
    Instant clientOccurredAt,
    @NotNull String decision,
    Long approvedAmount,
    String reason
) { }
