package com.b201.ecovillage.realtime.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PublicDisplayResponse(
    @JsonProperty("activity_id") UUID activityId,
    String scene,
    Timer timer,
    @JsonProperty("public_data") Map<String, Object> publicData
) {
    public record Timer(
        @JsonProperty("server_time") Instant serverTime,
        @JsonProperty("ends_at") Instant endsAt
    ) {
    }
}
