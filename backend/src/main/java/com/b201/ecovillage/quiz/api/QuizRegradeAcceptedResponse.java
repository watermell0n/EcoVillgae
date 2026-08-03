package com.b201.ecovillage.quiz.api;

import java.util.UUID;

public record QuizRegradeAcceptedResponse(
    UUID operationId,
    String status,
    String statusUrl
) {
}
