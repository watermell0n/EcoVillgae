package com.b201.ecovillage.identity.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CurrentUserView(
    UUID sessionId,
    UUID userId,
    String displayName,
    String role,
    Instant accessTokenExpiresAt,
    List<SchoolAccessView> schools,
    List<ClassroomAccessView> classrooms
) {
}
