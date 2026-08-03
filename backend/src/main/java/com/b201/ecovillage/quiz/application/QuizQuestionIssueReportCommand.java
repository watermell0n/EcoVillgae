package com.b201.ecovillage.quiz.application;

import java.util.UUID;

public record QuizQuestionIssueReportCommand(
    String studentSubject,
    UUID classroomId,
    UUID sessionId,
    UUID questionId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    String reasonCode,
    String description
) {
}
