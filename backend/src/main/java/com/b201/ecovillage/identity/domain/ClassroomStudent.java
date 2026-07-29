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
import jakarta.persistence.Version;

@Entity
@Table(name = "classroom_students")
public class ClassroomStudent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "salary_role_id")
    private UUID salaryRoleId;

    @Column(name = "roster_number", nullable = false)
    private int rosterNumber;

    @Column(nullable = false, length = 40)
    private String nickname;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ClassroomStudentStatus status;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClassroomStudent() {
    }

    private ClassroomStudent(
        UUID id,
        UUID classroomId,
        int rosterNumber,
        String nickname,
        Instant joinedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.rosterNumber = positiveRosterNumber(rosterNumber);
        this.nickname = validNickname(nickname);
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt must not be null");
        this.status = ClassroomStudentStatus.ACTIVE;
        this.stateVersion = 0;
        this.statusChangedAt = joinedAt;
        this.createdAt = joinedAt;
        this.updatedAt = joinedAt;
    }

    public static ClassroomStudent enroll(
        UUID id,
        UUID classroomId,
        int rosterNumber,
        String nickname,
        Instant joinedAt
    ) {
        return new ClassroomStudent(id, classroomId, rosterNumber, nickname, joinedAt);
    }

    public void assignSalaryRole(UUID salaryRoleId, Instant changedAt) {
        this.salaryRoleId = salaryRoleId;
        this.updatedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public void changeNickname(String nickname, Instant changedAt) {
        this.nickname = validNickname(nickname);
        this.updatedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public void retire(Instant retiredAt) {
        Instant changedAt = Objects.requireNonNull(retiredAt, "retiredAt must not be null");
        this.retiredAt = changedAt;
        this.status = ClassroomStudentStatus.RETIRED;
        this.statusChangedAt = changedAt;
        this.updatedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public UUID getSalaryRoleId() {
        return salaryRoleId;
    }

    public int getRosterNumber() {
        return rosterNumber;
    }

    public String getNickname() {
        return nickname;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getRetiredAt() {
        return retiredAt;
    }

    public ClassroomStudentStatus getStatus() {
        return status;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    private static int positiveRosterNumber(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("rosterNumber must be positive");
        }
        return value;
    }

    private static String validNickname(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("nickname must not be blank");
        }

        String normalized = value.trim();
        if (normalized.length() > 40) {
            throw new IllegalArgumentException("nickname must not exceed 40 characters");
        }
        return normalized;
    }
}
