package com.b201.ecovillage.ai.api;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

public record AnalysisPeriodRequest(
    @NotNull Instant from,
    @NotNull Instant to
) {
}
