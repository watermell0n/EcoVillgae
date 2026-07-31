package com.b201.ecovillage.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "student_credentials")
public class StudentCredential {

    @Id
    @Column(name = "classroom_student_id", nullable = false, updatable = false)
    private UUID classroomStudentId;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Version
    @Column(name = "credential_version", nullable = false)
    private long credentialVersion;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected StudentCredential() {
    }

    private StudentCredential(
        UUID classroomStudentId,
        UUID classroomId,
        String passwordHash,
        Instant changedAt
    ) {
        this.classroomStudentId = Objects.requireNonNull(
            classroomStudentId,
            "classroomStudentId must not be null"
        );
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.passwordHash = validPasswordHash(passwordHash);
        this.credentialVersion = 1;
        this.changedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public static StudentCredential issue(
        UUID classroomStudentId,
        UUID classroomId,
        String passwordHash,
        Instant changedAt
    ) {
        return new StudentCredential(classroomStudentId, classroomId, passwordHash, changedAt);
    }

    public void replacePasswordHash(String passwordHash, Instant changedAt) {
        this.passwordHash = validPasswordHash(passwordHash);
        this.changedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public UUID getClassroomStudentId() {
        return classroomStudentId;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public long getCredentialVersion() {
        return credentialVersion;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    private static String validPasswordHash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }
        return value;
    }
}
