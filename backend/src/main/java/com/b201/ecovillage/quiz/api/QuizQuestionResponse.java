package com.b201.ecovillage.quiz.api;

import java.util.UUID;

import com.b201.ecovillage.quiz.domain.QuizContentType;
import com.b201.ecovillage.quiz.domain.QuizQuestionType;

public record QuizQuestionResponse(
    UUID resourceId,
    String label,
    String status,
    int version,
    long stateVersion,
    QuizContentType contentType,
    QuizQuestionType questionType,
    String prompt
) {
}
