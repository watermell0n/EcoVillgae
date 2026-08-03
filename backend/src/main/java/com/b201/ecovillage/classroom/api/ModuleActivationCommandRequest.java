package com.b201.ecovillage.classroom.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ModuleActivationCommandRequest(
    @NotNull @Min(0) Long expectedStateVersion,
    @Pattern(regexp = "^SCR-[A-Z]+-\\d{3}$") String screenId,
    @Pattern(regexp = "^UIA-[A-Z]+-\\d{3}$") String uiActionId,
    Instant clientOccurredAt,
    boolean confirmPrerequisites,
    Map<String, Object> initialPolicyValues,
    List<String> recommendationActionIds
) {
}
