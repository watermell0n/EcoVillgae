package com.b201.ecovillage.quiz.api;

import java.util.UUID;

public record QuizLeaderboardEntryResponse(
    UUID resourceId,
    String label,
    String status,
    int version,
    long stateVersion,
    int rank,
    String nickname,
    String characterCode
) {
}
