package com.b201.ecovillage.identity.application;

import java.time.Instant;
import java.util.UUID;

public record SessionRefreshResult(
    String accessToken,
    String refreshToken,
    Instant accessTokenExpiresAt,
    Instant refreshTokenExpiresAt,
    UUID sessionId,
    String role
) {
}
