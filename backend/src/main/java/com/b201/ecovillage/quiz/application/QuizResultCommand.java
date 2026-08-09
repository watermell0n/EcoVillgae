package com.b201.ecovillage.quiz.application;

import java.util.UUID;

public record QuizResultCommand(
    String teacherSubject,
    UUID classroomId,
    UUID sessionId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    Integer expectedResultVersion,
    QuizResultCommandType command,
    Boolean leaderboardPublic,
    Boolean rewardWarningAcknowledged
) {
}
