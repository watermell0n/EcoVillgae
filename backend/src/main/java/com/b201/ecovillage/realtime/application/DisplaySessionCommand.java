package com.b201.ecovillage.realtime.application;

import java.util.UUID;

public record DisplaySessionCommand(
    String teacherSubject,
    UUID classroomId,
    UUID activityId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    DisplaySessionCommandType command,
    UUID displaySessionId
) {
}
