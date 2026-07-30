package com.b201.ecovillage.quiz.application;

import java.util.UUID;

public record QuizResourceResult(
    UUID quizResourceId,
    String status,
    long stateVersion
) {
}
