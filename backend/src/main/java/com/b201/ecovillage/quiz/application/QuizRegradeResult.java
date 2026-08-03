package com.b201.ecovillage.quiz.application;

import java.util.UUID;

public record QuizRegradeResult(
    UUID operationId,
    String status
) {
}
