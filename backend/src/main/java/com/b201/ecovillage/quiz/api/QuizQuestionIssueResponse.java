package com.b201.ecovillage.quiz.api;

import java.util.UUID;

public record QuizQuestionIssueResponse(
    UUID quizResourceId,
    String status,
    long stateVersion
) {
}
