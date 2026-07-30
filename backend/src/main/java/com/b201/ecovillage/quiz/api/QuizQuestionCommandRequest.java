package com.b201.ecovillage.quiz.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.b201.ecovillage.quiz.application.QuizQuestionCommandType;
import com.b201.ecovillage.quiz.domain.QuizContentType;
import com.b201.ecovillage.quiz.domain.QuizQuestionType;

public record QuizQuestionCommandRequest(
    @Min(0) Long expectedStateVersion,
    @Size(max = 120) String screenId,
    @Size(max = 120) String uiActionId,
    Instant clientOccurredAt,
    @NotNull QuizQuestionCommandType command,
    UUID questionId,
    @NotNull QuizContentType contentType,
    @NotNull QuizQuestionType questionType,
    @NotBlank String prompt,
    @NotNull @Size(min = 2, max = 10) List<@Valid QuizOptionRequest> options,
    String explanation,
    @Min(0) Integer expectedQuestionVersion
) {
}
