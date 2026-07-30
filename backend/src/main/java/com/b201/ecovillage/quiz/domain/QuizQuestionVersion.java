package com.b201.ecovillage.quiz.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_question_versions")
public class QuizQuestionVersion {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "quiz_question_id", nullable = false, updatable = false)
    private UUID quizQuestionId;

    @Column(nullable = false, updatable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, updatable = false, length = 40)
    private QuizContentType contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, updatable = false, length = 40)
    private QuizQuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private QuizQuestionStatus status;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String prompt;

    @Column(updatable = false, columnDefinition = "text")
    private String explanation;

    @Column(nullable = false, updatable = false, length = 40)
    private String source;

    @Column(name = "ai_run_id", updatable = false)
    private UUID aiRunId;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected QuizQuestionVersion() {
    }

    private QuizQuestionVersion(
        UUID id,
        UUID classroomId,
        UUID quizQuestionId,
        int version,
        QuizContentType contentType,
        QuizQuestionType questionType,
        String prompt,
        String explanation,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.quizQuestionId = Objects.requireNonNull(
            quizQuestionId,
            "quizQuestionId must not be null"
        );
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        this.version = version;
        this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
        this.questionType = Objects.requireNonNull(questionType, "questionType must not be null");
        this.prompt = required(prompt, "prompt");
        this.explanation = normalized(explanation);
        this.source = "TEACHER";
        this.status = QuizQuestionStatus.DRAFT;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static QuizQuestionVersion teacherDraft(
        UUID id,
        UUID classroomId,
        UUID quizQuestionId,
        int version,
        QuizContentType contentType,
        QuizQuestionType questionType,
        String prompt,
        String explanation,
        Instant createdAt
    ) {
        return new QuizQuestionVersion(
            id,
            classroomId,
            quizQuestionId,
            version,
            contentType,
            questionType,
            prompt,
            explanation,
            createdAt
        );
    }

    public void approve() {
        if (
            status != QuizQuestionStatus.DRAFT
                && status != QuizQuestionStatus.REVIEW_REQUIRED
        ) {
            throw new IllegalStateException("question version cannot be approved");
        }
        status = QuizQuestionStatus.APPROVED;
    }

    public void publish(Instant publishedAt) {
        if (status != QuizQuestionStatus.APPROVED) {
            throw new IllegalStateException("question version cannot be published");
        }
        status = QuizQuestionStatus.PUBLISHED;
        this.publishedAt = Objects.requireNonNull(
            publishedAt,
            "publishedAt must not be null"
        );
    }

    public UUID getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public QuizContentType getContentType() {
        return contentType;
    }

    public QuizQuestionType getQuestionType() {
        return questionType;
    }

    public QuizQuestionStatus getStatus() {
        return status;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getExplanation() {
        return explanation;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String normalized(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
