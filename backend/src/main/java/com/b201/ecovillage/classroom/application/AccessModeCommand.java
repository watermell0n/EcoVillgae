package com.b201.ecovillage.classroom.application;

import java.util.UUID;
import java.util.List;

public record AccessModeCommand(
    String teacherSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    AccessModeCommandType command,
    Integer durationMinutes,
    UUID preflightRunId,
    List<String> preflightWarningAcknowledgements
) {
    public AccessModeCommand {
        preflightWarningAcknowledgements = preflightWarningAcknowledgements == null
            ? List.of()
            : List.copyOf(preflightWarningAcknowledgements);
    }
}
