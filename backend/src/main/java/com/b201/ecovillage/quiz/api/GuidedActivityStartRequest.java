package com.b201.ecovillage.quiz.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GuidedActivityStartRequest(
    @JsonProperty("expected_state_version")
    @PositiveOrZero
    Long expectedStateVersion,
    @JsonProperty("screen_id") @Size(max = 120) String screenId,
    @JsonProperty("ui_action_id") @Size(max = 120) String uiActionId,
    @JsonProperty("client_occurred_at") Instant clientOccurredAt,
    @JsonProperty("participant_ids") @NotNull List<@NotNull UUID> participantIds,
    @JsonProperty("public_fields") @NotNull List<@NotNull String> publicFields
) {
}
