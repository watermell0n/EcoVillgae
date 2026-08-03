package com.b201.ecovillage.quiz.api;

import java.util.List;
import java.util.Map;

public record QuizResultsResponse(
    int resultVersion,
    String status,
    Map<String, Object> summary,
    List<QuizLeaderboardEntryResponse> leaderboard
) {
}
