package com.b201.ecovillage.classroom.api;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PayoutResponse(
    @JsonProperty("transaction_group_id")
    UUID transactionGroupId,
    String status,
    @JsonProperty("state_version")
    long stateVersion
) {
}
