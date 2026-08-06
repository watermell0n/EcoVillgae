package com.b201.ecovillage.classroom.api;

import java.util.UUID;

public record ClassroomArchiveResponse(
    UUID classroomId,
    String status,
    long stateVersion
) {
}
