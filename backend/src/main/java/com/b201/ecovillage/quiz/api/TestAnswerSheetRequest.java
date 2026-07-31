package com.b201.ecovillage.quiz.api;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TestAnswerSheetRequest(
    @Min(0) Long expectedStateVersion,
    @Size(max = 120) String screenId,
    @Size(max = 120) String uiActionId,
    Instant clientOccurredAt,
    @NotNull List<@Valid TestAnswerRequest> answers,
    @Min(0) int answerSheetVersion,
    boolean submit
) {
}
