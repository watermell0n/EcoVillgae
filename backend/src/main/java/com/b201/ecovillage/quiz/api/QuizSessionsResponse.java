package com.b201.ecovillage.quiz.api;

import java.util.List;

public record QuizSessionsResponse(List<QuizSessionResponse> sessions) {
    public QuizSessionsResponse {
        sessions = List.copyOf(sessions);
    }
}
