package com.b201.ecovillage.identity.domain;

import java.time.Duration;
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
@Table(name = "classrooms")
public class Classroom {

    private static final Duration STUDENT_READ_ONLY_ACCESS = Duration.ofDays(30);

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_code", nullable = false, updatable = false, length = 8)
    private String classroomCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ClassroomStatus status;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    protected Classroom() {
    }

    private Classroom(
        UUID id,
        String classroomCode,
        ClassroomStatus status,
        Instant statusChangedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomCode = Objects.requireNonNull(classroomCode, "classroomCode must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.statusChangedAt = Objects.requireNonNull(
            statusChangedAt,
            "statusChangedAt must not be null"
        );
    }

    public static Classroom active(UUID id, String classroomCode, Instant createdAt) {
        return new Classroom(id, classroomCode, ClassroomStatus.ACTIVE, createdAt);
    }

    public void changeStatus(ClassroomStatus status, Instant changedAt) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.statusChangedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public boolean allowsStudentLogin(Instant now) {
        return switch (status) {
            case ACTIVE, CLOSING -> true;
            case READ_ONLY -> now.isBefore(statusChangedAt.plus(STUDENT_READ_ONLY_ACCESS));
            case ARCHIVED -> false;
        };
    }

    public UUID getId() {
        return id;
    }

    public String getClassroomCode() {
        return classroomCode;
    }

    public ClassroomStatus getStatus() {
        return status;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }
}
