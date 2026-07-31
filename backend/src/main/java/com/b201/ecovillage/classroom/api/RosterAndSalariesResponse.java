package com.b201.ecovillage.classroom.api;

import java.util.UUID;

public record RosterAndSalariesResponse(
    UUID classroomId,
    int studentCount,
    int salaryRoleCount,
    long stateVersion
) {
}
