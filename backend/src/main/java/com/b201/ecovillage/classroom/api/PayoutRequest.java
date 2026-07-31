package com.b201.ecovillage.classroom.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.b201.ecovillage.classroom.application.PayoutType;

public record PayoutRequest(
    @JsonProperty("expected_state_version")
    @PositiveOrZero
    Long expectedStateVersion,

    @JsonProperty("screen_id")
    String screenId,

    @JsonProperty("ui_action_id")
    String uiActionId,

    @JsonProperty("client_occurred_at")
    Instant clientOccurredAt,

    @JsonProperty("payout_type")
    @NotNull
    PayoutType payoutType,

    @JsonProperty("student_ids")
    @NotEmpty
    List<@NotNull UUID> studentIds,

    @JsonProperty("period_key")
    @NotBlank
    @Size(max = 40)
    String periodKey,

    @PositiveOrZero
    long amount,

    @JsonProperty("policy_version")
    @PositiveOrZero
    int policyVersion,

    @NotBlank
    @Size(max = 500)
    String reason
) {
}
