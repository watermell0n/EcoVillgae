package com.b201.ecovillage.ai.api;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * API-AI-004 입력 계약이다. 계약에 선언하지 않은 최상위 필드는 받지 않는다.
 */
public record EconomicEventDraftRequest(
    Long expectedStateVersion,
    String screenId,
    String uiActionId,
    Instant clientOccurredAt,

    @NotBlank
    String templateType,

    String interestRateScope,

    @Size(max = 1000)
    String teacherContext
) {
}
