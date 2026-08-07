package com.b201.ecovillage.classroom.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StudentActivityResponse(
    @JsonProperty("resource_id") UUID resourceId,
    @JsonProperty("student_id") UUID studentId,
    @JsonProperty("activity_type") String activityType,
    String title,
    Integer quantity,
    long amount,
    String status,
    @JsonProperty("occurred_at") Instant occurredAt
) {
}
