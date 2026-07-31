package com.b201.ecovillage.quiz.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.b201.ecovillage.quiz.domain.QuizContentType;
import com.b201.ecovillage.quiz.domain.QuizMode;

public record QuizSessionCommand(
    String teacherSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    UUID sessionId,
    QuizSessionCommandType command,
    List<UUID> questionVersionIds,
    List<UUID> participantIds,
    QuizMode mode,
    QuizContentType contentType,
    Map<String, Object> rewardPolicyPublic,
    boolean leaderboardPublic
) {
}
