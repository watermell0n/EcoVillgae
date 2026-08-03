package com.b201.ecovillage.quiz.application;

import java.util.UUID;

public record QuizLeaderboardEntryView(
    UUID resultId,
    int rank,
    String nickname,
    String characterCode,
    int resultVersion
) {
}
