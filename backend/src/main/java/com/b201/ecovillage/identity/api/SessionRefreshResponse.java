package com.b201.ecovillage.identity.api;

import java.time.Instant;
import java.util.UUID;

public record SessionRefreshResponse(
    String accessToken,
    String refreshToken,
    Instant expiresAt,
    UUID sessionId,
    String role
) {
}
