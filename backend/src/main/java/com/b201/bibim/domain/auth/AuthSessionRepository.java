package com.b201.bibim.domain.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, UUID> {

    Optional<AuthSessionEntity> findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
            String tokenHash,
            Instant now
    );

    @Modifying
    @Query("""
            update AuthSessionEntity session
               set session.revokedAt = :revokedAt
             where session.studentId = :studentId
               and session.revokedAt is null
               and session.expiresAt > :revokedAt
            """)
    int revokeActiveStudentSessions(
            @Param("studentId") UUID studentId,
            @Param("revokedAt") Instant revokedAt
    );
}
