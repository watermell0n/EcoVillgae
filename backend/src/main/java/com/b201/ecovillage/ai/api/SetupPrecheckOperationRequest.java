package com.b201.ecovillage.ai.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

import com.b201.ecovillage.ai.application.SetupPrecheckInput;

public record SetupPrecheckOperationRequest(
    @NotBlank String analysisMode,
    @Valid SetupPrecheckInput triggerContext,
    @Valid AnalysisPeriodRequest period,
    List<String> metricScopes
) {
    public SetupPrecheckOperationRequest {
        metricScopes = metricScopes == null ? List.of() : List.copyOf(metricScopes);
    }
}
