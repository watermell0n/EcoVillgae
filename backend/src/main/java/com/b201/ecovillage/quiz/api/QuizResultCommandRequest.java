package com.b201.ecovillage.quiz.api;

import java.time.Instant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.b201.ecovillage.quiz.application.QuizResultCommandType;

public record QuizResultCommandRequest(
    @Min(0) Long expectedStateVersion,
    String screenId,
    String uiActionId,
    Instant clientOccurredAt,
    @NotNull QuizResultCommandType command,
    Boolean leaderboardPublic,
    @Min(0) Integer expectedResultVersion,
    Boolean rewardWarningAcknowledged
) {
}
