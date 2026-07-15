package com.b201.bibim.domain.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_sessions")
public class AuthSessionEntity {

    @Id
    private UUID id;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "principal_type", nullable = false, length = 20)
    private String principalType;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "login_version")
    private Integer loginVersion;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected AuthSessionEntity() {
    }

    public static AuthSessionEntity teacher(String tokenHash, UUID userId, Instant expiresAt) {
        return new AuthSessionEntity(tokenHash, "TEACHER", userId, null, null, expiresAt);
    }

    public static AuthSessionEntity student(
            String tokenHash,
            UUID studentId,
            int loginVersion,
            Instant expiresAt
    ) {
        return new AuthSessionEntity(tokenHash, "STUDENT", null, studentId, loginVersion, expiresAt);
    }

    private AuthSessionEntity(
            String tokenHash,
            String principalType,
            UUID userId,
            UUID studentId,
            Integer loginVersion,
            Instant expiresAt
    ) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.tokenHash = tokenHash;
        this.principalType = principalType;
        this.userId = userId;
        this.studentId = studentId;
        this.loginVersion = loginVersion;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.lastSeenAt = now;
    }

    public String getPrincipalType() {
        return principalType;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public Integer getLoginVersion() {
        return loginVersion;
    }
}
