package com.b201.ecovillage.store.api;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StorePurchaseResponse(
    @JsonProperty("order_id")
    UUID orderId,
    String status,
    @JsonProperty("total_amount")
    long totalAmount,
    @JsonProperty("state_version")
    long stateVersion
) {
}
