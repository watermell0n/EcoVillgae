package com.b201.ecovillage.store.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StorePurchaseRequest(
    @JsonProperty("expected_state_version")
    @PositiveOrZero
    Long expectedStateVersion,

    @JsonProperty("screen_id")
    String screenId,

    @JsonProperty("ui_action_id")
    String uiActionId,

    @JsonProperty("client_occurred_at")
    Instant clientOccurredAt,

    @JsonProperty("product_id")
    @NotNull
    UUID productId,

    @Positive
    int quantity,

    @JsonProperty("expected_product_version")
    @Positive
    int expectedProductVersion
) {
}
