package com.b201.ecovillage.ai.application;

import java.time.Instant;
import java.util.UUID;

/**
 * API-AI-006 응답이다. status는 계약상 APPROVED와 REJECTED만 쓴다.
 * 정책 효과가 반영된 이벤트도 교사에게는 APPROVED로 보이고, 게시 시각은 publishedAt이 알린다.
 */
public record EconomicEventDecisionResult(
    UUID eventId,
    UUID aiRunId,
    String status,
    long stateVersion,
    Instant publishedAt
) {
}
