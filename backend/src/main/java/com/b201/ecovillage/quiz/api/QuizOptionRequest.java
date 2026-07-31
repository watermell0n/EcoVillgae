package com.b201.ecovillage.quiz.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuizOptionRequest(
    @NotBlank @Size(max = 20) String optionKey,
    @NotBlank String optionText,
    boolean isCorrect,
    @Min(0) int sortOrder
) {
}
