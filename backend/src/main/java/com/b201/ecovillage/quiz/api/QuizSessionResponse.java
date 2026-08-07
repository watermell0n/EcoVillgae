package com.b201.ecovillage.quiz.api;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

import com.b201.ecovillage.quiz.domain.QuizContentType;
import com.b201.ecovillage.quiz.domain.QuizMode;

public record QuizSessionResponse(
    UUID quizSessionId,
    QuizMode mode,
    QuizContentType contentType,
    String status,
    long stateVersion,
    Instant openedAt,
    List<QuizSessionQuestionResponse> questions
) {
}
