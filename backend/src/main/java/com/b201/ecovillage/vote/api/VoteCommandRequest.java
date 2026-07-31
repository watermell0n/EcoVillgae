package com.b201.ecovillage.vote.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.b201.ecovillage.vote.application.VoteCommandType;
import com.fasterxml.jackson.annotation.JsonProperty;

public record VoteCommandRequest(
    @JsonProperty("expected_state_version")
    @PositiveOrZero
    Long expectedStateVersion,
    @JsonProperty("screen_id") @Size(max = 120) String screenId,
    @JsonProperty("ui_action_id") @Size(max = 120) String uiActionId,
    @JsonProperty("client_occurred_at") Instant clientOccurredAt,
    VoteCommandType command,
    @Size(max = 160) String title,
    @Size(max = 2_000) String description,
    @Valid @Size(max = 10) List<VoteOptionRequest> options,
    @JsonProperty("participant_ids")
    List<UUID> participantIds,
    @JsonProperty("public_display_enabled")
    Boolean publicDisplayEnabled,
    @JsonProperty("selected_use")
    @Size(max = 500)
    String selectedUse
) {
}
