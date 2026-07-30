package com.b201.ecovillage.classroom.api;

import java.util.UUID;

public record CreateClassroomResponse(
    UUID classroomId,
    String classroomCode,
    String currencyName,
    String status,
    long stateVersion
) {
}
