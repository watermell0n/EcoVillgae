package com.b201.ecovillage.realtime.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DisplaySessionResponse(
    @JsonProperty("display_session_id") UUID displaySessionId,
    @JsonProperty("display_token") String displayToken,
    @JsonProperty("expires_at") Instant expiresAt,
    String status
) {
}
