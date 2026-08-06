package com.b201.ecovillage.classroom.application;

import java.util.UUID;

public record ClassroomArchiveResult(
    UUID classroomId,
    String status,
    long stateVersion
) {
}
