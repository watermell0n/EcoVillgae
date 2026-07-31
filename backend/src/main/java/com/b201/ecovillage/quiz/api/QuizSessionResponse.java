package com.b201.ecovillage.quiz.api;

import java.util.List;
import java.util.UUID;

import com.b201.ecovillage.quiz.domain.QuizMode;

public record QuizSessionResponse(
    UUID quizSessionId,
    QuizMode mode,
    String status,
    List<QuizSessionQuestionResponse> questions
) {
}
