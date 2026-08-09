package com.b201.ecovillage.quiz.application;

import java.util.List;
import java.util.Map;

public record QuizResultsView(
    int resultVersion,
    String status,
    Map<String, Object> summary,
    List<QuizLeaderboardEntryView> leaderboard,
    List<QuizQuestionResultView> questions
) {
    public QuizResultsView {
        summary = Map.copyOf(summary);
        leaderboard = List.copyOf(leaderboard);
        questions = List.copyOf(questions);
    }
}
