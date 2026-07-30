package com.b201.ecovillage.quiz.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_options")
public class QuizOption {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "quiz_question_version_id", nullable = false, updatable = false)
    private UUID quizQuestionVersionId;

    @Column(name = "option_key", nullable = false, updatable = false, length = 20)
    private String optionKey;

    @Column(name = "option_text", nullable = false, updatable = false, columnDefinition = "text")
    private String optionText;

    @Column(name = "is_correct", nullable = false, updatable = false)
    private boolean correct;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected QuizOption() {
    }

    private QuizOption(
        UUID id,
        UUID classroomId,
        UUID quizQuestionVersionId,
        String optionKey,
        String optionText,
        boolean correct,
        int sortOrder,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.quizQuestionVersionId = Objects.requireNonNull(
            quizQuestionVersionId,
            "quizQuestionVersionId must not be null"
        );
        this.optionKey = required(optionKey, 20, "optionKey");
        this.optionText = required(optionText, Integer.MAX_VALUE, "optionText");
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must not be negative");
        }
        this.correct = correct;
        this.sortOrder = sortOrder;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static QuizOption create(
        UUID id,
        UUID classroomId,
        UUID quizQuestionVersionId,
        String optionKey,
        String optionText,
        boolean correct,
        int sortOrder,
        Instant createdAt
    ) {
        return new QuizOption(
            id,
            classroomId,
            quizQuestionVersionId,
            optionKey,
            optionText,
            correct,
            sortOrder,
            createdAt
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getQuizQuestionVersionId() {
        return quizQuestionVersionId;
    }

    public String getOptionKey() {
        return optionKey;
    }

    public String getOptionText() {
        return optionText;
    }

    public boolean isCorrect() {
        return correct;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    private static String required(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return normalized;
    }
}
