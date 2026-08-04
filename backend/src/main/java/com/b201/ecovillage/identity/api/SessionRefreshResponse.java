package com.b201.ecovillage.identity.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionRefreshResponse(
    String accessToken,
    String refreshToken,
    Instant expiresAt,
    UUID sessionId,
    String role
) {
}
