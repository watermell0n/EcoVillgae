package com.b201.ecovillage.realtime.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.b201.ecovillage.realtime.application.DisplaySessionCommandType;

public record DisplaySessionCommandRequest(
    @JsonProperty("expected_state_version")
    @PositiveOrZero
    Long expectedStateVersion,
    @JsonProperty("screen_id") @Size(max = 120) String screenId,
    @JsonProperty("ui_action_id") @Size(max = 120) String uiActionId,
    @JsonProperty("client_occurred_at") Instant clientOccurredAt,
    @NotNull DisplaySessionCommandType command,
    @JsonProperty("display_session_id") UUID displaySessionId
) {
}
