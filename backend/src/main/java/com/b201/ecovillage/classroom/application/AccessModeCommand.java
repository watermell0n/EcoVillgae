package com.b201.ecovillage.classroom.application;

import java.util.UUID;

public record AccessModeCommand(
    String teacherSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    AccessModeCommandType command,
    Integer durationMinutes
) {
}
