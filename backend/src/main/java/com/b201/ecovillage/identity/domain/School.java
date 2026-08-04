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
@Table(name = "schools")
public class School {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "school_code", nullable = false, length = 40)
    private String schoolCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected School() {
    }

    private School(UUID id, String schoolCode, String name, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.schoolCode = requiredText(schoolCode, 40, "schoolCode");
        this.name = requiredText(name, 120, "name");
        this.status = AccountStatus.ACTIVE;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = createdAt;
    }

    public static School create(UUID id, String schoolCode, String name, Instant createdAt) {
        return new School(id, schoolCode, name, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public String getSchoolCode() {
        return schoolCode;
    }

    private static String requiredText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.trim();
    }
}
