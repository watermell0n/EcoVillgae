package com.b201.ecovillage.quiz.api;

import java.time.Instant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.b201.ecovillage.quiz.application.QuizQuestionStatusCommandType;

public record QuizQuestionStatusCommandRequest(
    @Min(0) Long expectedStateVersion,
    @Size(max = 120) String screenId,
    @Size(max = 120) String uiActionId,
    Instant clientOccurredAt,
    @NotNull QuizQuestionStatusCommandType command,
    @Size(max = 500) String reason
) {
}
