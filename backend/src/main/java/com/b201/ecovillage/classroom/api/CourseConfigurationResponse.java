package com.b201.ecovillage.classroom.api;

import java.util.UUID;

public record CourseConfigurationResponse(
    UUID classroomId,
    int policyVersion,
    long stateVersion,
    boolean replayed
) {
}
