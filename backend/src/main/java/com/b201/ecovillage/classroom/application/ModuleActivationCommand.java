package com.b201.ecovillage.classroom.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ModuleActivationCommand(
    String teacherSubject,
    UUID classroomId,
    String moduleCode,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    boolean confirmPrerequisites,
    Map<String, Object> initialPolicyValues,
    List<String> recommendationActionIds
) {
}
