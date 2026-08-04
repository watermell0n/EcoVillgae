package com.b201.ecovillage.ai.application;

import java.util.UUID;

public record AiSettingsCommand(
    String teacherSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    boolean enabled,
    String reason,
    Long expectedStateVersion
) {
}
