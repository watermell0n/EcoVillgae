package com.b201.ecovillage.identity.application;

import java.time.Instant;
import java.util.UUID;

public record StudentLoginResult(
    String accessToken,
    String refreshToken,
    Instant expiresAt,
    UUID classroomId,
    String currencyName,
    long walletBalance,
    UUID studentId
) {
}
