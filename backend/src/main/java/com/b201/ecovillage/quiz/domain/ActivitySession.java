package com.b201.ecovillage.quiz.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "activity_sessions")
public class ActivitySession {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "activity_type", nullable = false, updatable = false, length = 40)
    private String activityType;

    @Column(name = "policy_snapshot_id", updatable = false)
    private UUID policySnapshotId;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "public_display_enabled", nullable = false)
    private boolean publicDisplayEnabled;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(nullable = false, length = 40)
    private String status;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ActivitySession() {
    }

    private ActivitySession(
        UUID id,
        UUID classroomId,
        UUID createdByUserId,
        boolean publicDisplayEnabled,
        Instant startedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.createdByUserId = Objects.requireNonNull(
            createdByUserId,
            "createdByUserId must not be null"
        );
        this.activityType = "QUIZ";
        this.publicDisplayEnabled = publicDisplayEnabled;
        this.startsAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        this.status = "LIVE";
        this.statusChangedAt = startedAt;
        this.createdAt = startedAt;
        this.updatedAt = startedAt;
    }

    public static ActivitySession liveQuiz(
        UUID id,
        UUID classroomId,
        UUID createdByUserId,
        boolean publicDisplayEnabled,
        Instant startedAt
    ) {
        return new ActivitySession(
            id,
            classroomId,
            createdByUserId,
            publicDisplayEnabled,
            startedAt
        );
    }

    public void beginFinalizing(Instant changedAt) {
        if (!"LIVE".equals(status)) {
            throw new IllegalStateException("only a live activity can begin finalizing");
        }
        status = "FINALIZING";
        statusChangedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        updatedAt = changedAt;
    }

    public Instant getStartsAt() {
        return startsAt;
    }
}
