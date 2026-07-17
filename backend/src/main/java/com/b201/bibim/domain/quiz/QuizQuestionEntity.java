package com.b201.bibim.domain.quiz;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "quiz_questions")
public class QuizQuestionEntity {

    @Id
    private UUID id;

    @Column(name = "quiz_id", nullable = false)
    private UUID quizId;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Column(name = "question_type", nullable = false, length = 30)
    private String questionType;

    @Column(nullable = false, columnDefinition = "text")
    private String prompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<QuizOption> options;

    @Column(name = "correct_answer", nullable = false, length = 50)
    private String correctAnswer;

    @Column(columnDefinition = "text")
    private String explanation;

    protected QuizQuestionEntity() {
    }

    public static QuizQuestionEntity multipleChoice(
            UUID quizId,
            int orderNo,
            String prompt,
            List<QuizOption> options,
            String correctAnswer,
            String explanation
    ) {
        QuizQuestionEntity question = new QuizQuestionEntity();
        question.id = UUID.randomUUID();
        question.quizId = quizId;
        question.orderNo = orderNo;
        question.questionType = "MULTIPLE_CHOICE";
        question.prompt = prompt;
        question.options = List.copyOf(options);
        question.correctAnswer = correctAnswer;
        question.explanation = explanation;
        return question;
    }

    public boolean accepts(String answer) {
        return options.stream().anyMatch(option -> option.id().equals(answer));
    }

    public boolean isCorrect(String answer) {
        return correctAnswer.equals(answer);
    }

    public UUID getId() {
        return id;
    }

    public UUID getQuizId() {
        return quizId;
    }

    public int getOrderNo() {
        return orderNo;
    }

    public String getQuestionType() {
        return questionType;
    }

    public String getPrompt() {
        return prompt;
    }

    public List<QuizOption> getOptions() {
        return List.copyOf(options);
    }

    public record QuizOption(String id, String text) {
    }
}
