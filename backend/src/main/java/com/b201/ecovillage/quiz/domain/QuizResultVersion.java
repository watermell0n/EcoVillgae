package com.b201.ecovillage.quiz.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_result_versions")
public class QuizResultVersion {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "quiz_session_id", nullable = false, updatable = false)
    private UUID quizSessionId;

    @Column(nullable = false, updatable = false)
    private int version;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "reward_transaction_group_id")
    private UUID rewardTransactionGroupId;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "supersedes_result_version_id", updatable = false)
    private UUID supersedesResultVersionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected QuizResultVersion() {
    }

    private QuizResultVersion(
        UUID id,
        UUID classroomId,
        UUID quizSessionId,
        int version,
        String status,
        UUID supersedesResultVersionId,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.quizSessionId = Objects.requireNonNull(quizSessionId, "quizSessionId must not be null");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        this.version = version;
        this.status = required(status, "status");
        this.supersedesResultVersionId = supersedesResultVersionId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.publishedAt = "PUBLISHED".equals(status) ? createdAt : null;
    }

    public static QuizResultVersion reviewing(
        UUID id,
        UUID classroomId,
        UUID quizSessionId,
        Instant createdAt
    ) {
        return new QuizResultVersion(
            id,
            classroomId,
            quizSessionId,
            1,
            "REVIEWING",
            null,
            createdAt
        );
    }

    public static QuizResultVersion superseding(
        UUID id,
        UUID classroomId,
        UUID quizSessionId,
        int version,
        UUID supersedesResultVersionId,
        boolean published,
        Instant createdAt
    ) {
        return new QuizResultVersion(
            id,
            classroomId,
            quizSessionId,
            version,
            published ? "PUBLISHED" : "REVIEWING",
            Objects.requireNonNull(
                supersedesResultVersionId,
                "supersedesResultVersionId must not be null"
            ),
            createdAt
        );
    }

    public void publish(UUID transactionGroupId, Instant publishedAt) {
        if (!"REVIEWING".equals(status)) {
            throw new IllegalStateException("only a reviewing result can be published");
        }
        status = "PUBLISHED";
        rewardTransactionGroupId = transactionGroupId;
        this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt must not be null");
    }

    public void attachRewardTransaction(UUID transactionGroupId) {
        if (!"PUBLISHED".equals(status)) {
            throw new IllegalStateException("only a published result can receive a reward transaction");
        }
        rewardTransactionGroupId = transactionGroupId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public UUID getQuizSessionId() {
        return quizSessionId;
    }

    public int getVersion() {
        return version;
    }

    public String getStatus() {
        return status;
    }

    public UUID getRewardTransactionGroupId() {
        return rewardTransactionGroupId;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public UUID getSupersedesResultVersionId() {
        return supersedesResultVersionId;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
