package com.b201.ecovillage.quiz.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "public_fields", nullable = false)
    private Set<String> publicFields;

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
        String activityType,
        UUID createdByUserId,
        boolean publicDisplayEnabled,
        String status,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.createdByUserId = Objects.requireNonNull(
            createdByUserId,
            "createdByUserId must not be null"
        );
        this.activityType = requiredText(activityType, "activityType");
        this.publicDisplayEnabled = publicDisplayEnabled;
        this.publicFields = Set.of();
        this.startsAt = "LIVE".equals(status) ? createdAt : null;
        this.status = requiredText(status, "status");
        this.statusChangedAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
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
            "QUIZ",
            createdByUserId,
            publicDisplayEnabled,
            "LIVE",
            startedAt
        );
    }

    public static ActivitySession ready(
        UUID id,
        UUID classroomId,
        String activityType,
        UUID createdByUserId,
        boolean publicDisplayEnabled,
        Instant createdAt
    ) {
        return new ActivitySession(
            id,
            classroomId,
            activityType,
            createdByUserId,
            publicDisplayEnabled,
            "READY",
            createdAt
        );
    }

    public void start(Instant changedAt, Set<String> publicFields) {
        if (!"READY".equals(status)) {
            throw new IllegalStateException("only a ready activity can start");
        }
        this.publicFields = Set.copyOf(
            Objects.requireNonNull(publicFields, "publicFields must not be null")
        );
        startsAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        changeStatus("LIVE", changedAt);
    }

    public void beginFinalizing(Instant changedAt) {
        if (!"LIVE".equals(status)) {
            throw new IllegalStateException("only a live activity can begin finalizing");
        }
        endsAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        changeStatus("FINALIZING", changedAt);
    }

    public void cancel(Instant changedAt) {
        if (!"READY".equals(status) && !"LIVE".equals(status)) {
            throw new IllegalStateException("only a ready or live activity can be canceled");
        }
        endsAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        changeStatus("CANCELED", changedAt);
    }

    public void complete(Instant changedAt) {
        if (!"FINALIZING".equals(status)) {
            throw new IllegalStateException(
                "only a finalizing activity can be completed"
            );
        }
        changeStatus("COMPLETED", changedAt);
    }

    private void changeStatus(String nextStatus, Instant changedAt) {
        status = nextStatus;
        statusChangedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        updatedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public String getActivityType() {
        return activityType;
    }

    public boolean isPublicDisplayEnabled() {
        return publicDisplayEnabled;
    }

    public Set<String> getPublicFields() {
        return Set.copyOf(publicFields);
    }

    public String getStatus() {
        return status;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
