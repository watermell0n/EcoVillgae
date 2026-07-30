package com.b201.ecovillage.quiz.application;

import java.util.UUID;

public record QuizQuestionStatusCommand(
    String teacherSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    UUID questionId,
    QuizQuestionStatusCommandType command,
    String reason
) {
}
