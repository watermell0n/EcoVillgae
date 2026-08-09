package com.b201.ecovillage.quiz.application;

import java.util.UUID;

/** A student's answer review, exposed only after quiz results are published. */
public record QuizQuestionResultView(
    UUID questionId,
    int sequenceNumber,
    String prompt,
    String selectedOptionKey,
    String selectedOptionText,
    String correctOptionKey,
    String correctOptionText,
    boolean correct,
    boolean invalidated,
    int scoreAwarded,
    String explanation
) {
}
