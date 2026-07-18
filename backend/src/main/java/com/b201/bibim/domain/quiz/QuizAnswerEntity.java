package com.b201.bibim.domain.quiz;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_answers")
public class QuizAnswerEntity {

    @Id
    private UUID id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(nullable = false, length = 50)
    private String answer;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;

    protected QuizAnswerEntity() {
    }

    private QuizAnswerEntity(
            UUID submissionId,
            UUID questionId,
            String answer,
            boolean correct
    ) {
        this.id = UUID.randomUUID();
        this.submissionId = submissionId;
        this.questionId = questionId;
        change(answer, correct);
    }

    public static QuizAnswerEntity create(
            UUID submissionId,
            UUID questionId,
            String answer,
            boolean correct
    ) {
        return new QuizAnswerEntity(submissionId, questionId, answer, correct);
    }

    public void change(String answer, boolean correct) {
        this.answer = answer;
        this.correct = correct;
        this.answeredAt = Instant.now();
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public UUID getSubmissionId() {
        return submissionId;
    }

    public String getAnswer() {
        return answer;
    }

    public boolean isCorrect() {
        return correct;
    }
}
