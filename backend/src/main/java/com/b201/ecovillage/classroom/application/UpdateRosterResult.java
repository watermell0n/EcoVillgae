package com.b201.ecovillage.classroom.application;

import java.util.UUID;

public record UpdateRosterResult(
    UUID classroomId,
    int studentCount,
    int salaryRoleCount,
    long stateVersion
) {
}
