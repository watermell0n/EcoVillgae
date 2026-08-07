package com.b201.ecovillage.classroom.application;

import java.time.Instant;
import java.util.UUID;

public record StudentActivityView(
    UUID resourceId,
    UUID studentId,
    String activityType,
    String title,
    Integer quantity,
    long amount,
    String status,
    Instant occurredAt
) {
}
