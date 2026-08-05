package com.b201.ecovillage.quiz.application;

import java.util.List;
import java.util.UUID;

public record QuizSessionQuestionView(
    UUID resourceId,
    UUID quizQuestionVersionId,
    String label,
    String status,
    int version,
    long stateVersion,
    String prompt,
    List<String> options
) {
}
