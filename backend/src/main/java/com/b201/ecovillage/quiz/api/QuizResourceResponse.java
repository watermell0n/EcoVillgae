package com.b201.ecovillage.quiz.api;

import java.util.UUID;

public record QuizResourceResponse(
    UUID quizResourceId,
    String status,
    long stateVersion
) {
}
