package com.b201.ecovillage.classroom.application;

import java.util.UUID;

public record CreateClassroomResult(
    UUID classroomId,
    String classroomCode,
    String currencyName,
    String status,
    long stateVersion
) {
}
