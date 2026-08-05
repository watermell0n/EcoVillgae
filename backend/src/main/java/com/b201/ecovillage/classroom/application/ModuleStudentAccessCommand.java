package com.b201.ecovillage.classroom.application;

import java.util.UUID;

public record ModuleStudentAccessCommand(
    String teacherSubject,
    UUID classroomId,
    String moduleCode,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    ModuleStudentAccessCommandType command,
    String reason
) {
}
