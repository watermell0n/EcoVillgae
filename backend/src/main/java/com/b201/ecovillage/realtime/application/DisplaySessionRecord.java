package com.b201.ecovillage.realtime.application;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record DisplaySessionRecord(
    UUID displaySessionId,
    UUID classroomId,
    UUID activityId,
    Set<String> publicFields,
    UUID issuedByUserId,
    Instant issuedAt,
    Instant expiresAt
) {
    public DisplaySessionRecord {
        publicFields = Set.copyOf(publicFields);
    }
}
