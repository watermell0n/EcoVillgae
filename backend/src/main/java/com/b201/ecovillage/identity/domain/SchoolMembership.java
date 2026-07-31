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
@Table(name = "school_memberships")
public class SchoolMembership {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SchoolMembershipRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AccountStatus status;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SchoolMembership() {
    }

    private SchoolMembership(
        UUID id,
        UUID schoolId,
        UUID userId,
        SchoolMembershipRole role,
        Instant assignedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.schoolId = Objects.requireNonNull(schoolId, "schoolId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.status = AccountStatus.ACTIVE;
        this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt must not be null");
        this.createdAt = assignedAt;
        this.updatedAt = assignedAt;
    }

    public static SchoolMembership assignTeacher(
        UUID id,
        UUID schoolId,
        UUID userId,
        Instant assignedAt
    ) {
        return new SchoolMembership(id, schoolId, userId, SchoolMembershipRole.TEACHER, assignedAt);
    }

    public void end(Instant endedAt) {
        this.endedAt = Objects.requireNonNull(endedAt, "endedAt must not be null");
        this.status = AccountStatus.INACTIVE;
        this.updatedAt = endedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public UUID getUserId() {
        return userId;
    }

    public SchoolMembershipRole getRole() {
        return role;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
