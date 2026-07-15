package com.b201.bibim.domain.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, UUID> {

    Optional<AuthSessionEntity> findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
            String tokenHash,
            Instant now
    );
}
