package com.b201.ecovillage.identity.application;

import java.time.Instant;
import java.util.UUID;

public record TeacherSessionRecord(
    UUID sessionId,
    UUID userId,
    String externalSubject,
    String refreshTokenHash,
    TeacherSessionStatus status,
    Instant issuedAt,
    Instant expiresAt,
    Instant revokedAt
) {

    public boolean isActiveAt(Instant now) {
        return status == TeacherSessionStatus.ACTIVE
            && revokedAt == null
            && expiresAt.isAfter(now);
    }
}
