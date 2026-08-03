package com.b201.ecovillage.quiz.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_student_results")
public class QuizStudentResult {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "quiz_result_version_id", nullable = false, updatable = false)
    private UUID quizResultVersionId;

    @Column(name = "classroom_student_id", nullable = false, updatable = false)
    private UUID classroomStudentId;

    @Column(nullable = false, updatable = false)
    private int score;

    @Column(name = "correct_count", nullable = false, updatable = false)
    private int correctCount;

    @Column(updatable = false)
    private Integer rank;

    @Column(name = "reward_amount", nullable = false, updatable = false)
    private long rewardAmount;

    @Column(name = "total_response_time_ms", updatable = false)
    private Long totalResponseTimeMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected QuizStudentResult() {
    }

    private QuizStudentResult(
        UUID id,
        UUID classroomId,
        UUID quizResultVersionId,
        UUID classroomStudentId,
        int score,
        int correctCount,
        Integer rank,
        long rewardAmount,
        long totalResponseTimeMs,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.quizResultVersionId = Objects.requireNonNull(
            quizResultVersionId,
            "quizResultVersionId must not be null"
        );
        this.classroomStudentId = Objects.requireNonNull(
            classroomStudentId,
            "classroomStudentId must not be null"
        );
        if (score < 0 || correctCount < 0 || rewardAmount < 0 || totalResponseTimeMs < 0) {
            throw new IllegalArgumentException("quiz result values must not be negative");
        }
        if (rank != null && rank < 1) {
            throw new IllegalArgumentException("rank must be positive");
        }
        this.score = score;
        this.correctCount = correctCount;
        this.rank = rank;
        this.rewardAmount = rewardAmount;
        this.totalResponseTimeMs = totalResponseTimeMs;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static QuizStudentResult scored(
        UUID id,
        UUID classroomId,
        UUID quizResultVersionId,
        UUID classroomStudentId,
        int score,
        int correctCount,
        Integer rank,
        long rewardAmount,
        long totalResponseTimeMs,
        Instant createdAt
    ) {
        return new QuizStudentResult(
            id,
            classroomId,
            quizResultVersionId,
            classroomStudentId,
            score,
            correctCount,
            rank,
            rewardAmount,
            totalResponseTimeMs,
            createdAt
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getQuizResultVersionId() {
        return quizResultVersionId;
    }

    public UUID getClassroomStudentId() {
        return classroomStudentId;
    }

    public int getScore() {
        return score;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public Integer getRank() {
        return rank;
    }

    public long getRewardAmount() {
        return rewardAmount;
    }

    public long getTotalResponseTimeMs() {
        return totalResponseTimeMs == null ? 0L : totalResponseTimeMs;
    }
}
