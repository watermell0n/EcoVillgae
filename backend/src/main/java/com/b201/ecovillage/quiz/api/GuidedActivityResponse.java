package com.b201.ecovillage.quiz.api;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GuidedActivityResponse(
    @JsonProperty("activity_id") UUID activityId,
    String status,
    @JsonProperty("state_version") long stateVersion
) {
}
