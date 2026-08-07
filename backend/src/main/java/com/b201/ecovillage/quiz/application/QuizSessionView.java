package com.b201.ecovillage.quiz.application;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

import com.b201.ecovillage.quiz.domain.QuizContentType;
import com.b201.ecovillage.quiz.domain.QuizMode;

public record QuizSessionView(
    UUID quizSessionId,
    QuizMode mode,
    QuizContentType contentType,
    String status,
    long stateVersion,
    Instant openedAt,
    List<QuizSessionQuestionView> questions
) {
}
