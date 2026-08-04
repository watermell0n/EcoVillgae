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
import jakarta.persistence.Version;

@Entity
@Table(name = "quiz_questions")
public class QuizQuestion {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 40)
    private QuizContentType contentType;

    @Column(length = 160)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private QuizQuestionStatus status;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected QuizQuestion() {
    }

    private QuizQuestion(
        UUID id,
        UUID classroomId,
        UUID createdByUserId,
        QuizContentType contentType,
        String prompt,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.createdByUserId = Objects.requireNonNull(
            createdByUserId,
            "createdByUserId must not be null"
        );
        this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
        this.title = title(prompt);
        this.status = QuizQuestionStatus.DRAFT;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = createdAt;
        this.statusChangedAt = createdAt;
    }

    public static QuizQuestion draft(
        UUID id,
        UUID classroomId,
        UUID createdByUserId,
        QuizContentType contentType,
        String prompt,
        Instant createdAt
    ) {
        return new QuizQuestion(
            id,
            classroomId,
            createdByUserId,
            contentType,
            prompt,
            createdAt
        );
    }

    public void revise(QuizContentType contentType, String prompt, Instant changedAt) {
        if (status == QuizQuestionStatus.RETIRED) {
            throw new IllegalStateException("retired question cannot be revised");
        }
        this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
        this.title = title(prompt);
        changeStatus(QuizQuestionStatus.DRAFT, changedAt);
    }

    public void approve(Instant changedAt) {
        if (
            status != QuizQuestionStatus.DRAFT
                && status != QuizQuestionStatus.REVIEW_REQUIRED
        ) {
            throw new IllegalStateException("only a draft or reviewed question can be approved");
        }
        changeStatus(QuizQuestionStatus.APPROVED, changedAt);
    }

    public void requireReview(Instant changedAt) {
        if (status != QuizQuestionStatus.DRAFT) {
            throw new IllegalStateException("only a draft question can require review");
        }
        changeStatus(QuizQuestionStatus.REVIEW_REQUIRED, changedAt);
    }

    public void publish(Instant changedAt) {
        if (status != QuizQuestionStatus.APPROVED) {
            throw new IllegalStateException("only an approved question can be published");
        }
        changeStatus(QuizQuestionStatus.PUBLISHED, changedAt);
    }

    public void retire(Instant changedAt) {
        if (status != QuizQuestionStatus.PUBLISHED) {
            throw new IllegalStateException("only a published question can be retired");
        }
        changeStatus(QuizQuestionStatus.RETIRED, changedAt);
    }

    private void changeStatus(QuizQuestionStatus status, Instant changedAt) {
        this.status = status;
        this.statusChangedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        this.updatedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public QuizContentType getContentType() {
        return contentType;
    }

    public String getTitle() {
        return title;
    }

    public QuizQuestionStatus getStatus() {
        return status;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    private static String title(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        String normalized = prompt.trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }
}
