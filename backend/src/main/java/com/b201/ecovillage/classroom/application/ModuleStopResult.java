package com.b201.ecovillage.classroom.application;

import java.util.List;
import java.util.UUID;

public record ModuleStopResult(
    UUID moduleId,
    String moduleCode,
    String status,
    long stateVersion,
    boolean cleanupRequired,
    List<String> cleanupBlockers,
    boolean replayed
) {
}
