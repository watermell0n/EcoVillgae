package com.b201.ecovillage.identity.security;

import java.time.Instant;
import java.util.UUID;

public record AccessTokenClaims(
    String subject,
    UUID sessionId,
    String role,
    UUID userId,
    UUID classroomId,
    long credentialVersion,
    Instant issuedAt,
    Instant expiresAt
) {
}
