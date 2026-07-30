package com.b201.ecovillage.classroom.api;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.b201.ecovillage.classroom.application.AccessModeCommandType;

public record AccessModeCommandRequest(
    @JsonProperty("expected_state_version")
    @PositiveOrZero
    Long expectedStateVersion,

    @JsonProperty("screen_id")
    @Size(max = 120)
    String screenId,

    @JsonProperty("ui_action_id")
    @Size(max = 120)
    String uiActionId,

    @JsonProperty("client_occurred_at")
    Instant clientOccurredAt,

    @NotNull
    AccessModeCommandType command,

    @JsonProperty("duration_minutes")
    @Min(1)
    @Max(480)
    Integer durationMinutes
) {
}
