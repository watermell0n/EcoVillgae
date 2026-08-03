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
@Table(name = "regrading_runs")
public class RegradingRun {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "quiz_session_id", nullable = false, updatable = false)
    private UUID quizSessionId;

    @Column(name = "source_result_version_id", nullable = false, updatable = false)
    private UUID sourceResultVersionId;

    @Column(name = "new_result_version_id")
    private UUID newResultVersionId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "started_by_user_id", nullable = false, updatable = false)
    private UUID startedByUserId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

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

    protected RegradingRun() {
    }

    private RegradingRun(
        UUID id,
        UUID classroomId,
        UUID quizSessionId,
        UUID sourceResultVersionId,
        String reason,
        UUID startedByUserId,
        Instant startedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.quizSessionId = Objects.requireNonNull(quizSessionId, "quizSessionId must not be null");
        this.sourceResultVersionId = Objects.requireNonNull(
            sourceResultVersionId,
            "sourceResultVersionId must not be null"
        );
        this.reason = required(reason);
        this.startedByUserId = Objects.requireNonNull(
            startedByUserId,
            "startedByUserId must not be null"
        );
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        this.status = "RUNNING";
        this.statusChangedAt = startedAt;
        this.createdAt = startedAt;
        this.updatedAt = startedAt;
    }

    public static RegradingRun start(
        UUID id,
        UUID classroomId,
        UUID quizSessionId,
        UUID sourceResultVersionId,
        String reason,
        UUID startedByUserId,
        Instant startedAt
    ) {
        return new RegradingRun(
            id,
            classroomId,
            quizSessionId,
            sourceResultVersionId,
            reason,
            startedByUserId,
            startedAt
        );
    }

    public void complete(UUID newResultVersionId, Instant completedAt) {
        if (!"RUNNING".equals(status)) {
            throw new IllegalStateException("only a running regrade can complete");
        }
        this.newResultVersionId = Objects.requireNonNull(
            newResultVersionId,
            "newResultVersionId must not be null"
        );
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
        this.status = "COMPLETED";
        this.statusChangedAt = completedAt;
        this.updatedAt = completedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getQuizSessionId() {
        return quizSessionId;
    }

    public UUID getNewResultVersionId() {
        return newResultVersionId;
    }

    public String getStatus() {
        return status;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return value.trim();
    }
}
