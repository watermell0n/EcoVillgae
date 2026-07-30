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
@Table(name = "quiz_submissions")
public class QuizSubmission {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "quiz_session_id", nullable = false, updatable = false)
    private UUID quizSessionId;

    @Column(name = "classroom_student_id", nullable = false, updatable = false)
    private UUID classroomStudentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private QuizMode mode;

    @Column(name = "answer_sheet_version", nullable = false, updatable = false)
    private int answerSheetVersion;

    @Column(name = "submitted_at", updatable = false)
    private Instant submittedAt;

    @Column(nullable = false, updatable = false, length = 40)
    private String status;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "status_changed_at", nullable = false, updatable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, updatable = false)
    private Instant updatedAt;

    protected QuizSubmission() {
    }

    private QuizSubmission(
        UUID id,
        UUID classroomId,
        UUID quizSessionId,
        UUID classroomStudentId,
        QuizMode mode,
        int answerSheetVersion,
        boolean submitted,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.quizSessionId = Objects.requireNonNull(quizSessionId, "quizSessionId must not be null");
        this.classroomStudentId = Objects.requireNonNull(
            classroomStudentId,
            "classroomStudentId must not be null"
        );
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
        if (answerSheetVersion < 1) {
            throw new IllegalArgumentException("answerSheetVersion must be positive");
        }
        this.answerSheetVersion = answerSheetVersion;
        this.submittedAt = submitted ? createdAt : null;
        this.status = submitted ? "SUBMITTED" : "IN_PROGRESS";
        this.statusChangedAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static QuizSubmission create(
        UUID id,
        UUID classroomId,
        UUID quizSessionId,
        UUID classroomStudentId,
        QuizMode mode,
        int answerSheetVersion,
        boolean submitted,
        Instant createdAt
    ) {
        return new QuizSubmission(
            id,
            classroomId,
            quizSessionId,
            classroomStudentId,
            mode,
            answerSheetVersion,
            submitted,
            createdAt
        );
    }

    public UUID getId() {
        return id;
    }

    public int getAnswerSheetVersion() {
        return answerSheetVersion;
    }

    public String getStatus() {
        return status;
    }

    public long getStateVersion() {
        return stateVersion;
    }
}
