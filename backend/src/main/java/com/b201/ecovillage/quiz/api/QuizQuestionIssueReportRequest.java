package com.b201.ecovillage.quiz.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QuizQuestionIssueReportRequest(
    @Min(0) Long expectedStateVersion,
    String screenId,
    String uiActionId,
    Instant clientOccurredAt,
    @NotNull UUID questionId,
    @NotBlank @Size(max = 80) String reasonCode,
    @NotBlank @Size(max = 2_000) String description
) {
}
