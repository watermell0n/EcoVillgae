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
@Table(name = "users")
public class UserAccount {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "external_subject", nullable = false, updatable = false, length = 128)
    private String externalSubject;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 40)
    private UserType userType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccount() {
    }

    private UserAccount(
        UUID id,
        String externalSubject,
        String displayName,
        UserType userType,
        AccountStatus status,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.externalSubject = requiredText(externalSubject, 128, "externalSubject");
        this.displayName = requiredText(displayName, 120, "displayName");
        this.userType = Objects.requireNonNull(userType, "userType must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = createdAt;
    }

    public static UserAccount createTeacher(
        UUID id,
        String externalSubject,
        String displayName,
        Instant createdAt
    ) {
        return new UserAccount(
            id,
            externalSubject,
            displayName,
            UserType.TEACHER,
            AccountStatus.ACTIVE,
            createdAt
        );
    }

    public void rename(String displayName, Instant changedAt) {
        this.displayName = requiredText(displayName, 120, "displayName");
        this.updatedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public void deactivate(Instant changedAt) {
        this.status = AccountStatus.INACTIVE;
        this.updatedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public UUID getId() {
        return id;
    }

    public String getExternalSubject() {
        return externalSubject;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserType getUserType() {
        return userType;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String requiredText(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }

        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }
}
