package com.b201.ecovillage.quiz.application;

import java.util.List;
import java.util.UUID;

import com.b201.ecovillage.quiz.domain.QuizContentType;
import com.b201.ecovillage.quiz.domain.QuizQuestionType;

public record QuizQuestionView(
    UUID resourceId,
    String label,
    String status,
    int version,
    long stateVersion,
    QuizContentType contentType,
    QuizQuestionType questionType,
    String prompt,
    String explanation,
    String source,
    UUID aiRunId,
    List<Option> options
) {
    public QuizQuestionView {
        options = List.copyOf(options);
    }

    public record Option(
        UUID optionId,
        String optionKey,
        String optionText,
        boolean correct,
        int sortOrder
    ) {
    }
}
