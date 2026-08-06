package com.b201.ecovillage.classroom.api;

import java.util.UUID;

public record ModuleStudentAccessResponse(
    UUID moduleId,
    String moduleCode,
    String status,
    boolean studentAccessSuspended,
    long stateVersion,
    boolean replayed
) {
}
