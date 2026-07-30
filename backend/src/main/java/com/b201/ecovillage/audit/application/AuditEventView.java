package com.b201.ecovillage.audit.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventView(
    UUID auditEventId,
    String actorType,
    UUID actorId,
    String action,
    String targetType,
    UUID targetId,
    String requestId,
    String correlationId,
    Map<String, Object> beforeSummary,
    Map<String, Object> afterSummary,
    Instant occurredAt
) {
}
