package com.b201.ecovillage.audit.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuditEventResponse(
    @JsonProperty("audit_event_id") UUID auditEventId,
    @JsonProperty("actor_type") String actorType,
    @JsonProperty("actor_id") UUID actorId,
    String action,
    @JsonProperty("target_type") String targetType,
    @JsonProperty("target_id") UUID targetId,
    @JsonProperty("request_id") String requestId,
    @JsonProperty("correlation_id") String correlationId,
    @JsonProperty("before_summary") Map<String, Object> beforeSummary,
    @JsonProperty("after_summary") Map<String, Object> afterSummary,
    @JsonProperty("occurred_at") Instant occurredAt
) {
}
