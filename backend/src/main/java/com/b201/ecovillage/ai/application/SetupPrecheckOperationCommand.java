package com.b201.ecovillage.ai.application;

import java.util.UUID;

public record SetupPrecheckOperationCommand(
    String teacherSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    SetupPrecheckInput input
) {
}
