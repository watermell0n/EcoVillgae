package com.b201.ecovillage.vote.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VoteBallotRequest(
    @JsonProperty("expected_state_version")
    @PositiveOrZero
    Long expectedStateVersion,
    @JsonProperty("screen_id") @Size(max = 120) String screenId,
    @JsonProperty("ui_action_id") @Size(max = 120) String uiActionId,
    @JsonProperty("client_occurred_at") Instant clientOccurredAt,
    @JsonProperty("option_id") @NotNull UUID optionId
) {
}
