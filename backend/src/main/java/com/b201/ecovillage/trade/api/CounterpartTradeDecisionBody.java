package com.b201.ecovillage.trade.api;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.b201.ecovillage.trade.application.CounterpartTradeDecision;

public record CounterpartTradeDecisionBody(
    Long expectedStateVersion,
    @Size(max = 120) String screenId,
    @Size(max = 120) String uiActionId,
    Instant clientOccurredAt,
    @NotNull CounterpartTradeDecision decision,
    @Size(max = 1000) String reason
) {
}
