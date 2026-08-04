package com.b201.ecovillage.ai.api;

import java.time.Instant;
import java.util.UUID;

/**
 * API-AI-006 응답이다. contracts/openapi.yaml이 additionalProperties=false로 닫아 두었으므로
 * event_id, ai_run_id, status, state_version, published_at 다섯 필드만 내려보낸다.
 */
public record EconomicEventDecisionResponse(
    UUID eventId,
    UUID aiRunId,
    String status,
    long stateVersion,
    Instant publishedAt
) {
}
