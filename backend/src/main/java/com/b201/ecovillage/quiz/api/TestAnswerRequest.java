package com.b201.ecovillage.quiz.api;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record TestAnswerRequest(
    @NotNull UUID questionId,
    @NotNull UUID optionId
) {
}
