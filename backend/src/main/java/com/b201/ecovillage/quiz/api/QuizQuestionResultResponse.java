package com.b201.ecovillage.quiz.api;

import java.util.UUID;

public record QuizQuestionResultResponse(
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
