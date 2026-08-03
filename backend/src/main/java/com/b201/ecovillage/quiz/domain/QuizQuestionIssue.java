package com.b201.ecovillage.quiz.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
    name = "quiz_question_issues",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_quiz_question_issues_student_report",
        columnNames = {"quiz_session_question_id", "reported_by_student_id"}
    )
)
public class QuizQuestionIssue {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "quiz_session_question_id", nullable = false, updatable = false)
    private UUID quizSessionQuestionId;

    @Column(name = "reported_by_student_id", updatable = false)
    private UUID reportedByStudentId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String reason;

    @Column(columnDefinition = "text")
    private String resolution;

    @Column(name = "resolved_by_user_id")
    private UUID resolvedByUserId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

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

    protected QuizQuestionIssue() {
    }

    private QuizQuestionIssue(
        UUID id,
        UUID classroomId,
        UUID quizSessionQuestionId,
        UUID reportedByStudentId,
        String reasonCode,
        String description,
        Instant reportedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.quizSessionQuestionId = Objects.requireNonNull(
            quizSessionQuestionId,
            "quizSessionQuestionId must not be null"
        );
        this.reportedByStudentId = Objects.requireNonNull(
            reportedByStudentId,
            "reportedByStudentId must not be null"
        );
        this.reason = required(reasonCode, "reasonCode")
            + ": " + required(description, "description");
        this.status = "UNDER_REVIEW";
        this.statusChangedAt = Objects.requireNonNull(reportedAt, "reportedAt must not be null");
        this.createdAt = reportedAt;
        this.updatedAt = reportedAt;
    }

    public static QuizQuestionIssue report(
        UUID id,
        UUID classroomId,
        UUID quizSessionQuestionId,
        UUID reportedByStudentId,
        String reasonCode,
        String description,
        Instant reportedAt
    ) {
        return new QuizQuestionIssue(
            id,
            classroomId,
            quizSessionQuestionId,
            reportedByStudentId,
            reasonCode,
            description,
            reportedAt
        );
    }

    public void invalidate(String reason, UUID resolvedByUserId, Instant resolvedAt) {
        if (!"UNDER_REVIEW".equals(status)) {
            throw new IllegalStateException("only an issue under review can be invalidated");
        }
        this.resolution = required(reason, "reason");
        this.resolvedByUserId = Objects.requireNonNull(
            resolvedByUserId,
            "resolvedByUserId must not be null"
        );
        this.resolvedAt = Objects.requireNonNull(resolvedAt, "resolvedAt must not be null");
        this.status = "INVALIDATED";
        this.statusChangedAt = resolvedAt;
        this.updatedAt = resolvedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getQuizSessionQuestionId() {
        return quizSessionQuestionId;
    }

    public UUID getReportedByStudentId() {
        return reportedByStudentId;
    }

    public String getStatus() {
        return status;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
