package com.b201.ecovillage.quiz.application;

import java.util.List;
import java.util.UUID;

public record GuidedActivityStartCommand(
    String teacherSubject,
    UUID classroomId,
    UUID activityId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    List<UUID> participantIds,
    List<String> publicFields
) {
}
