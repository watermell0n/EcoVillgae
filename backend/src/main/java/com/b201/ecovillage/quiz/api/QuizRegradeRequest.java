package com.b201.ecovillage.quiz.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QuizRegradeRequest(
    @Min(0) Long expectedStateVersion,
    String screenId,
    String uiActionId,
    Instant clientOccurredAt,
    @NotEmpty Set<@NotNull UUID> invalidatedQuestionIds,
    @NotBlank @Size(max = 2_000) String reason,
    @NotNull @Min(0) Integer expectedResultVersion
) {
}
