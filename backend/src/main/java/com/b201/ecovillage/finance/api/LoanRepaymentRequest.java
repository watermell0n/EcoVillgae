package com.b201.ecovillage.finance.api;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

public record LoanRepaymentRequest(
    @JsonProperty("expected_state_version") Long expectedStateVersion,
    @JsonProperty("screen_id") String screenId,
    @JsonProperty("ui_action_id") String uiActionId,
    @JsonProperty("client_occurred_at") Instant clientOccurredAt,
    @NotBlank String command
) { }
