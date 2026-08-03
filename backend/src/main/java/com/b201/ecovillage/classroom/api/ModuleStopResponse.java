package com.b201.ecovillage.classroom.api;

import java.util.List;
import java.util.UUID;

public record ModuleStopResponse(
    UUID moduleId,
    String moduleCode,
    String status,
    long stateVersion,
    boolean cleanupRequired,
    List<String> cleanupBlockers,
    boolean replayed
) {
}
