package com.b201.ecovillage.quiz.application;

import java.util.UUID;

public record QuizQuestionIssueResult(
    UUID issueId,
    String status,
    long stateVersion
) {
}
