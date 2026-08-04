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
@Table(name = "teacher_credentials")
public class TeacherCredential {

    @Id
    @Column(name = "teacher_user_id", nullable = false, updatable = false)
    private UUID teacherUserId;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Version
    @Column(name = "credential_version", nullable = false)
    private long credentialVersion;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected TeacherCredential() {
    }

    private TeacherCredential(UUID teacherUserId, String email, String passwordHash, Instant changedAt) {
        this.teacherUserId = Objects.requireNonNull(teacherUserId, "teacherUserId must not be null");
        this.email = requiredText(email, "email");
        this.passwordHash = requiredText(passwordHash, "passwordHash");
        this.credentialVersion = 1;
        this.changedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public static TeacherCredential issue(
        UUID teacherUserId,
        String email,
        String passwordHash,
        Instant changedAt
    ) {
        return new TeacherCredential(teacherUserId, email, passwordHash, changedAt);
    }

    public UUID getTeacherUserId() {
        return teacherUserId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
