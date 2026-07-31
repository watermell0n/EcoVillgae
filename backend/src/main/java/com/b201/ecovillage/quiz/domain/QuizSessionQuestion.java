package com.b201.ecovillage.quiz.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "quiz_session_questions")
public class QuizSessionQuestion {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "quiz_session_id", nullable = false, updatable = false)
    private UUID quizSessionId;

    @Column(name = "quiz_question_version_id", nullable = false, updatable = false)
    private UUID quizQuestionVersionId;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private int sequenceNumber;

    @Column(name = "prompt_snapshot", nullable = false, updatable = false, columnDefinition = "text")
    private String promptSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options_snapshot", nullable = false, updatable = false)
    private List<Map<String, Object>> optionsSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "correct_answer_snapshot", nullable = false, updatable = false)
    private Map<String, Object> correctAnswerSnapshot;

    @Column(name = "score_value", nullable = false, updatable = false)
    private int scoreValue;

    @Column(name = "time_limit_seconds", updatable = false)
    private Integer timeLimitSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected QuizSessionQuestion() {
    }

    private QuizSessionQuestion(
        UUID id,
        UUID classroomId,
        UUID quizSessionId,
        UUID quizQuestionVersionId,
        int sequenceNumber,
        String promptSnapshot,
        List<Map<String, Object>> optionsSnapshot,
        String correctOptionKey,
        int scoreValue,
        Integer timeLimitSeconds,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.quizSessionId = Objects.requireNonNull(quizSessionId, "quizSessionId must not be null");
        this.quizQuestionVersionId = Objects.requireNonNull(
            quizQuestionVersionId,
            "quizQuestionVersionId must not be null"
        );
        if (sequenceNumber < 1 || scoreValue < 0) {
            throw new IllegalArgumentException("question sequence and score are invalid");
        }
        if (timeLimitSeconds != null && timeLimitSeconds < 1) {
            throw new IllegalArgumentException("timeLimitSeconds must be positive");
        }
        this.sequenceNumber = sequenceNumber;
        this.promptSnapshot = required(promptSnapshot);
        this.optionsSnapshot = optionsSnapshot.stream()
            .map(LinkedHashMap::new)
            .map(Map::copyOf)
            .toList();
        this.correctAnswerSnapshot = Map.of(
            "option_key",
            required(correctOptionKey)
        );
        this.scoreValue = scoreValue;
        this.timeLimitSeconds = timeLimitSeconds;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static QuizSessionQuestion snapshot(
        UUID id,
        UUID classroomId,
        UUID quizSessionId,
        UUID quizQuestionVersionId,
        int sequenceNumber,
        String prompt,
        List<Map<String, Object>> options,
        String correctOptionKey,
        int scoreValue,
        Integer timeLimitSeconds,
        Instant createdAt
    ) {
        return new QuizSessionQuestion(
            id,
            classroomId,
            quizSessionId,
            quizQuestionVersionId,
            sequenceNumber,
            prompt,
            options,
            correctOptionKey,
            scoreValue,
            timeLimitSeconds,
            createdAt
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getQuizQuestionVersionId() {
        return quizQuestionVersionId;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public String getPromptSnapshot() {
        return promptSnapshot;
    }

    public List<Map<String, Object>> getOptionsSnapshot() {
        return optionsSnapshot.stream().map(Map::copyOf).toList();
    }

    public String correctOptionKey() {
        return String.valueOf(correctAnswerSnapshot.get("option_key"));
    }

    public int getScoreValue() {
        return scoreValue;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        return value.trim();
    }
}
