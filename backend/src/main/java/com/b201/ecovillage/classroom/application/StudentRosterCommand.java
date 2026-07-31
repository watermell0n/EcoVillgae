package com.b201.ecovillage.classroom.application;

import java.util.UUID;

public record StudentRosterCommand(
    UUID studentId,
    int attendanceNumber,
    String nickname,
    String roleCode
) {
}
