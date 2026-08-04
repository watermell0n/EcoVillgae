package com.b201.ecovillage.identity.api;

import java.time.Instant;
import java.util.UUID;

public record StudentSessionResponse(
    String accessToken,
    String refreshToken,
    Instant expiresAt,
    UUID classroomId,
    String currencyName,
    long walletBalance,
    UUID studentId,
    String role
) {
}
