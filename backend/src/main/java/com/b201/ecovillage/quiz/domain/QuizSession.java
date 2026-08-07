package com.b201.ecovillage.quiz.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "quiz_sessions")
public class QuizSession {

    @Id
    @Column(name = "activity_session_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private QuizMode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, updatable = false, length = 40)
    private QuizContentType contentType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reward_policy_public", nullable = false)
    private Map<String, Object> rewardPolicyPublic;

    @Column(name = "leaderboard_public", nullable = false)
    private boolean leaderboardPublic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private QuizSessionStatus status;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected QuizSession() {
    }

    private QuizSession(
        UUID id,
        UUID classroomId,
        QuizMode mode,
        QuizContentType contentType,
        Map<String, Object> rewardPolicyPublic,
        boolean leaderboardPublic,
        Instant openedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
        this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
        this.rewardPolicyPublic = new LinkedHashMap<>(
            Objects.requireNonNull(rewardPolicyPublic, "rewardPolicyPublic must not be null")
        );
        this.leaderboardPublic = mode == QuizMode.GAME && leaderboardPublic;
        this.status = QuizSessionStatus.OPEN;
        this.statusChangedAt = Objects.requireNonNull(openedAt, "openedAt must not be null");
        this.createdAt = openedAt;
        this.updatedAt = openedAt;
    }

    public static QuizSession open(
        UUID id,
        UUID classroomId,
        QuizMode mode,
        QuizContentType contentType,
        Map<String, Object> rewardPolicyPublic,
        boolean leaderboardPublic,
        Instant openedAt
    ) {
        return new QuizSession(
            id,
            classroomId,
            mode,
            contentType,
            rewardPolicyPublic,
            leaderboardPublic,
            openedAt
        );
    }

    public void closeSubmissions(Instant changedAt) {
        if (status != QuizSessionStatus.OPEN) {
            throw new IllegalStateException("submissions are not open");
        }
        status = QuizSessionStatus.SCORING;
        statusChangedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        updatedAt = changedAt;
    }

    public void replaceRewardPolicy(Map<String, Object> rewardPolicy, Instant changedAt) {
        if (mode != QuizMode.GAME) {
            throw new IllegalStateException("a TEST session cannot receive a reward");
        }
        if (status != QuizSessionStatus.OPEN) {
            throw new IllegalStateException("only an open session can change its reward");
        }
        this.rewardPolicyPublic = new LinkedHashMap<>(
            Objects.requireNonNull(rewardPolicy, "rewardPolicy must not be null")
        );
        this.updatedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public void beginResultReview(Instant changedAt) {
        transition(QuizSessionStatus.SCORING, QuizSessionStatus.REVIEWING, changedAt);
    }

    public void publishResults(boolean requestedLeaderboardPublic, Instant changedAt) {
        if (mode == QuizMode.TEST && requestedLeaderboardPublic) {
            throw new IllegalStateException("a TEST result cannot publish a leaderboard");
        }
        leaderboardPublic = mode == QuizMode.GAME && requestedLeaderboardPublic;
        transition(QuizSessionStatus.REVIEWING, QuizSessionStatus.RESULT_PUBLISHED, changedAt);
    }

    public void closeResults(Instant changedAt) {
        transition(QuizSessionStatus.RESULT_PUBLISHED, QuizSessionStatus.CLOSED, changedAt);
    }

    public boolean acceptsAnswers() {
        return status == QuizSessionStatus.OPEN;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public QuizMode getMode() {
        return mode;
    }

    public QuizContentType getContentType() {
        return contentType;
    }

    public Map<String, Object> getRewardPolicyPublic() {
        return Map.copyOf(rewardPolicyPublic);
    }

    public boolean isLeaderboardPublic() {
        return leaderboardPublic;
    }

    public QuizSessionStatus getStatus() {
        return status;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private void transition(
        QuizSessionStatus expected,
        QuizSessionStatus next,
        Instant changedAt
    ) {
        if (status != expected) {
            throw new IllegalStateException(
                "quiz session must be " + expected + " before " + next
            );
        }
        status = next;
        statusChangedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        updatedAt = changedAt;
    }
}
