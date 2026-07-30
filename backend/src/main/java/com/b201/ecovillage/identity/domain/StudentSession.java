package com.b201.ecovillage.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_sessions")
public class StudentSession {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "classroom_student_id", nullable = false, updatable = false)
    private UUID classroomStudentId;

    @Column(name = "device_link_id")
    private UUID deviceLinkId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 128)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StudentSessionStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudentSession() {
    }

    private StudentSession(
        UUID id,
        UUID classroomId,
        UUID classroomStudentId,
        UUID deviceLinkId,
        String tokenHash,
        Instant expiresAt,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.classroomStudentId = Objects.requireNonNull(
            classroomStudentId,
            "classroomStudentId must not be null"
        );
        this.deviceLinkId = Objects.requireNonNull(deviceLinkId, "deviceLinkId must not be null");
        this.tokenHash = requireText(tokenHash, "tokenHash");
        this.status = StudentSessionStatus.ACTIVE;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = createdAt;
    }

    public static StudentSession issue(
        UUID id,
        UUID classroomId,
        UUID classroomStudentId,
        UUID deviceLinkId,
        String tokenHash,
        Instant expiresAt,
        Instant createdAt
    ) {
        return new StudentSession(
            id,
            classroomId,
            classroomStudentId,
            deviceLinkId,
            tokenHash,
            expiresAt,
            createdAt
        );
    }

    public void revoke(Instant revokedAt) {
        if (status != StudentSessionStatus.ACTIVE) {
            return;
        }
        Instant changedAt = Objects.requireNonNull(revokedAt, "revokedAt must not be null");
        this.status = StudentSessionStatus.REVOKED;
        this.revokedAt = changedAt;
        this.updatedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public UUID getClassroomStudentId() {
        return classroomStudentId;
    }

    public UUID getDeviceLinkId() {
        return deviceLinkId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public StudentSessionStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
