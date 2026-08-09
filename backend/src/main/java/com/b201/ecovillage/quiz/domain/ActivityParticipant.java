package com.b201.ecovillage.quiz.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "activity_participants")
public class ActivityParticipant {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "activity_session_id", nullable = false, updatable = false)
    private UUID activitySessionId;

    @Column(name = "classroom_student_id", nullable = false, updatable = false)
    private UUID classroomStudentId;

    @Column(name = "participant_status", nullable = false, length = 40)
    private String participantStatus;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ActivityParticipant() {
    }

    private ActivityParticipant(
        UUID id,
        UUID classroomId,
        UUID activitySessionId,
        UUID classroomStudentId,
        Instant joinedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.activitySessionId = Objects.requireNonNull(
            activitySessionId,
            "activitySessionId must not be null"
        );
        this.classroomStudentId = Objects.requireNonNull(
            classroomStudentId,
            "classroomStudentId must not be null"
        );
        this.participantStatus = "JOINED";
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt must not be null");
        this.createdAt = joinedAt;
    }

    public static ActivityParticipant joined(
        UUID id,
        UUID classroomId,
        UUID activitySessionId,
        UUID classroomStudentId,
        Instant joinedAt
    ) {
        return new ActivityParticipant(
            id,
            classroomId,
            activitySessionId,
            classroomStudentId,
            joinedAt
        );
    }

    public void complete(Instant changedAt) {
        if (!"JOINED".equals(participantStatus)) {
            return;
        }
        participantStatus = "COMPLETED";
        completedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public void rejoin() {
        if (!"COMPLETED".equals(participantStatus)) {
            return;
        }
        participantStatus = "JOINED";
        completedAt = null;
    }

    public void cancel(Instant changedAt) {
        if (!"JOINED".equals(participantStatus)) {
            return;
        }
        participantStatus = "CANCELED";
        completedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public UUID getClassroomStudentId() {
        return classroomStudentId;
    }

    public String getParticipantStatus() {
        return participantStatus;
    }
}
