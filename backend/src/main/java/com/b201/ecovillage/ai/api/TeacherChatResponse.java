package com.b201.ecovillage.ai.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * API-AI-001 성공 응답이다. 계약이 strict라 선언한 필드만 담는다.
 */
public record TeacherChatResponse(
    UUID aiRunId,
    String answer,
    PeriodPayload period,
    Instant dataAsOf,
    List<EvidenceItem> evidence,
    List<RecommendationItem> recommendations
) {

    public record PeriodPayload(Instant from, Instant to) {
    }

    public record EvidenceItem(String factId, String label, Object value, String unit) {
    }

    public record RecommendationItem(String text, List<String> sideEffects) {
    }
}
