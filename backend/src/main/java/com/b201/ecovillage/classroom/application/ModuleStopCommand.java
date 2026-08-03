package com.b201.ecovillage.classroom.application;

import java.util.UUID;

public record ModuleStopCommand(
    String teacherSubject,
    UUID classroomId,
    String moduleCode,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    String reason
) {
}
