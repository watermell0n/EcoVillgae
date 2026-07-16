package com.b201.bibim.domain.quiz;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_submissions")
public class QuizSubmissionEntity {

    @Id
    private UUID id;

    @Column(name = "quiz_id", nullable = false)
    private UUID quizId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected QuizSubmissionEntity() {
    }

    private QuizSubmissionEntity(UUID quizId, UUID studentId) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.quizId = quizId;
        this.studentId = studentId;
        this.status = "IN_PROGRESS";
        this.correctCount = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static QuizSubmissionEntity start(UUID quizId, UUID studentId) {
        return new QuizSubmissionEntity(quizId, studentId);
    }

    public void submit(int correctCount) {
        Instant now = Instant.now();
        this.status = "SUBMITTED";
        this.correctCount = correctCount;
        this.submittedAt = now;
        this.updatedAt = now;
    }

    public boolean isSubmitted() {
        return "SUBMITTED".equals(status);
    }

    public UUID getId() {
        return id;
    }

    public UUID getQuizId() {
        return quizId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public String getStatus() {
        return status;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
