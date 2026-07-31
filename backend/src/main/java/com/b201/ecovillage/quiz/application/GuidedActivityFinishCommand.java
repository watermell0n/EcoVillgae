package com.b201.ecovillage.quiz.application;

import java.util.UUID;

public record GuidedActivityFinishCommand(
    String teacherSubject,
    UUID classroomId,
    UUID activityId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    GuidedActivityFinishType command,
    String reason
) {
}
