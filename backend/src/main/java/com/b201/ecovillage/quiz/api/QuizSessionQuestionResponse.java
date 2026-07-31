package com.b201.ecovillage.quiz.api;

import java.util.List;
import java.util.UUID;

public record QuizSessionQuestionResponse(
    UUID resourceId,
    String label,
    String status,
    int version,
    long stateVersion,
    String prompt,
    List<String> options
) {
}
