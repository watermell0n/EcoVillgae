package com.b201.ecovillage.identity.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TeacherSessionStore {

    void save(TeacherSessionRecord session);

    Optional<TeacherSessionRecord> findBySessionId(UUID sessionId);

    Optional<TeacherSessionRecord> findByRefreshTokenHash(String refreshTokenHash);

    boolean rotate(
        UUID sessionId,
        String expectedRefreshTokenHash,
        String newRefreshTokenHash,
        Instant newExpiresAt,
        Instant rotatedAt
    );

    boolean revoke(UUID sessionId, Instant revokedAt);
}
