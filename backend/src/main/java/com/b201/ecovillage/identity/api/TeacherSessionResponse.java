package com.b201.ecovillage.identity.api;

import java.time.Instant;
import java.util.UUID;

public record TeacherSessionResponse(
    String accessToken,
    Instant expiresAt,
    UUID sessionId,
    UUID teacherId,
    String displayName,
    String role
) {
}
