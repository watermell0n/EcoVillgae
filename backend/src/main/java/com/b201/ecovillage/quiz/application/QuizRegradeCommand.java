package com.b201.ecovillage.quiz.application;

import java.util.Set;
import java.util.UUID;

public record QuizRegradeCommand(
    String teacherSubject,
    UUID classroomId,
    UUID sessionId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    int expectedResultVersion,
    Set<UUID> invalidatedQuestionIds,
    String reason
) {
    public QuizRegradeCommand {
        invalidatedQuestionIds = invalidatedQuestionIds == null
            ? null
            : Set.copyOf(invalidatedQuestionIds);
    }
}
