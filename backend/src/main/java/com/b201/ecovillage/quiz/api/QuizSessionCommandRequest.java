package com.b201.ecovillage.quiz.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.b201.ecovillage.quiz.application.QuizSessionCommandType;
import com.b201.ecovillage.quiz.domain.QuizContentType;
import com.b201.ecovillage.quiz.domain.QuizMode;

public record QuizSessionCommandRequest(
    @Min(0) Long expectedStateVersion,
    @Size(max = 120) String screenId,
    @Size(max = 120) String uiActionId,
    Instant clientOccurredAt,
    @NotNull QuizSessionCommandType command,
    @NotNull List<UUID> questionVersionIds,
    @NotNull List<UUID> participantIds,
    @NotNull QuizMode mode,
    @NotNull QuizContentType contentType,
    @NotNull Map<String, Object> rewardPolicyPublic,
    Boolean leaderboardPublic
) {
}
