package com.b201.ecovillage.identity.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(
    UUID sessionId,
    UUID userId,
    String displayName,
    String role,
    Instant accessTokenExpiresAt,
    List<SchoolAccessResponse> schools,
    List<ClassroomAccessResponse> classrooms
) {
}
