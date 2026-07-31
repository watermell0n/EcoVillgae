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
@Table(name = "classroom_teachers")
public class ClassroomTeacher {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ClassroomTeacherRole role;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ClassroomTeacher() {
    }

    private ClassroomTeacher(
        UUID id,
        UUID classroomId,
        UUID userId,
        ClassroomTeacherRole role,
        Instant assignedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt must not be null");
        this.createdAt = assignedAt;
    }

    public static ClassroomTeacher assign(
        UUID id,
        UUID classroomId,
        UUID userId,
        ClassroomTeacherRole role,
        Instant assignedAt
    ) {
        return new ClassroomTeacher(id, classroomId, userId, role, assignedAt);
    }

    public void end(Instant endedAt) {
        this.endedAt = Objects.requireNonNull(endedAt, "endedAt must not be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public UUID getUserId() {
        return userId;
    }

    public ClassroomTeacherRole getRole() {
        return role;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public boolean isActive() {
        return endedAt == null;
    }
}
