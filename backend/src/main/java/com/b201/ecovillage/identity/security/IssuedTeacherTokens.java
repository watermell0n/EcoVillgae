package com.b201.ecovillage.identity.security;

import java.time.Instant;

public record IssuedTeacherTokens(
    String accessToken,
    String refreshToken,
    String refreshTokenHash,
    Instant accessTokenExpiresAt,
    Instant refreshTokenExpiresAt
) {
}
