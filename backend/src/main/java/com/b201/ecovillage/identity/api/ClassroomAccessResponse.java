package com.b201.ecovillage.identity.api;

import java.util.UUID;

public record ClassroomAccessResponse(
    UUID classroomId,
    String classroomCode,
    String role
) {
}
