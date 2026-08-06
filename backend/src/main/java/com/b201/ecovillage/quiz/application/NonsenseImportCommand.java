package com.b201.ecovillage.quiz.application;

import java.util.UUID;

public record NonsenseImportCommand(
    String teacherSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    int grade,
    int questionCount
) {
}
