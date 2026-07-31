package com.b201.ecovillage.identity.application;

import java.util.UUID;

public record StudentLoginCommand(
    String classroomCode,
    int attendanceNumber,
    String nickname,
    String password,
    UUID deviceId,
    String remoteAddress
) {
}
