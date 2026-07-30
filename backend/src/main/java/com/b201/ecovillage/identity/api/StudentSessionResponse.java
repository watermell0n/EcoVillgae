package com.b201.ecovillage.identity.api;

import java.time.Instant;
import java.util.UUID;

public record StudentSessionResponse(
    String accessToken,
    String refreshToken,
    Instant expiresAt,
    UUID classroomId,
    UUID studentId,
    String role
) {
}
