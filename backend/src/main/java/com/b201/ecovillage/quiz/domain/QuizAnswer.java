package com.b201.ecovillage.quiz.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_answers")
public class QuizAnswer {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "quiz_submission_id", nullable = false, updatable = false)
    private UUID quizSubmissionId;

    @Column(name = "quiz_session_question_id", nullable = false, updatable = false)
    private UUID quizSessionQuestionId;

    @Column(name = "selected_option_key", updatable = false, length = 20)
    private String selectedOptionKey;

    @Column(name = "free_text", updatable = false, columnDefinition = "text")
    private String freeText;

    @Column(name = "is_correct", updatable = false)
    private Boolean correct;

    @Column(name = "score_awarded", updatable = false)
    private Integer scoreAwarded;

    @Column(name = "response_time_ms", updatable = false)
    private Integer responseTimeMs;

    @Column(name = "answered_at", nullable = false, updatable = false)
    private Instant answeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected QuizAnswer() {
    }

    private QuizAnswer(
        UUID id,
        UUID classroomId,
        UUID quizSubmissionId,
        UUID quizSessionQuestionId,
        String selectedOptionKey,
        boolean correct,
        int scoreAwarded,
        Integer responseTimeMs,
        Instant answeredAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.quizSubmissionId = Objects.requireNonNull(
            quizSubmissionId,
            "quizSubmissionId must not be null"
        );
        this.quizSessionQuestionId = Objects.requireNonNull(
            quizSessionQuestionId,
            "quizSessionQuestionId must not be null"
        );
        if (selectedOptionKey == null || selectedOptionKey.isBlank()) {
            throw new IllegalArgumentException("selectedOptionKey must not be blank");
        }
        if (scoreAwarded < 0 || (responseTimeMs != null && responseTimeMs < 0)) {
            throw new IllegalArgumentException("answer score and response time are invalid");
        }
        this.selectedOptionKey = selectedOptionKey.trim();
        this.correct = correct;
        this.scoreAwarded = scoreAwarded;
        this.responseTimeMs = responseTimeMs;
        this.answeredAt = Objects.requireNonNull(answeredAt, "answeredAt must not be null");
        this.createdAt = answeredAt;
    }

    public static QuizAnswer selected(
        UUID id,
        UUID classroomId,
        UUID quizSubmissionId,
        UUID quizSessionQuestionId,
        String selectedOptionKey,
        boolean correct,
        int scoreAwarded,
        Integer responseTimeMs,
        Instant answeredAt
    ) {
        return new QuizAnswer(
            id,
            classroomId,
            quizSubmissionId,
            quizSessionQuestionId,
            selectedOptionKey,
            correct,
            scoreAwarded,
            responseTimeMs,
            answeredAt
        );
    }

    public UUID getId() {
        return id;
    }
}
