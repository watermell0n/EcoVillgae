package com.b201.ecovillage.identity.application;

import java.util.UUID;

public record ClassroomAccessView(
    UUID classroomId,
    String classroomCode,
    String role
) {
}
