package com.b201.ecovillage.classroom.application;

import java.util.UUID;

public record CourseConfigurationUpdateResult(
    UUID classroomId,
    int policyVersion,
    long stateVersion,
    boolean replayed
) {
}
