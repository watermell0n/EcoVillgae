package com.b201.ecovillage.realtime.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StudentPresenceResponse(
    @JsonProperty("student_id") UUID studentId,
    String status,
    @JsonProperty("current_screen") String currentScreen,
    @JsonProperty("last_seen_at") Instant lastSeenAt
) {
}
