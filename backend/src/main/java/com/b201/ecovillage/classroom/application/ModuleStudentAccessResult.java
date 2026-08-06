package com.b201.ecovillage.classroom.application;

import java.util.UUID;

public record ModuleStudentAccessResult(
    UUID moduleId,
    String moduleCode,
    String status,
    boolean studentAccessSuspended,
    long stateVersion,
    boolean replayed
) {
}
