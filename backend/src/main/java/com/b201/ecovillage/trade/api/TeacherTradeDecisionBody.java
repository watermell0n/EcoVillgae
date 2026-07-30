package com.b201.ecovillage.trade.api;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.b201.ecovillage.trade.application.TeacherTradeDecision;

public record TeacherTradeDecisionBody(
    Long expectedStateVersion,
    @Size(max = 120) String screenId,
    @Size(max = 120) String uiActionId,
    Instant clientOccurredAt,
    @NotNull TeacherTradeDecision decision,
    @NotBlank @Size(max = 1000) String reason
) {
}
