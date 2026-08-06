package com.b201.ecovillage.quiz.application;

import java.util.List;
import java.util.UUID;

import com.b201.ecovillage.quiz.domain.QuizMode;

public record QuizSessionView(
    UUID quizSessionId,
    QuizMode mode,
    String status,
    long stateVersion,
    List<QuizSessionQuestionView> questions
) {
}
