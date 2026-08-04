package com.b201.ecovillage.ai.api;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * API-AI-005 입력 계약이다. enabled는 계약상 false만 허용한다.
 */
public record AiSettingsRequest(
    Long expectedStateVersion,
    String screenId,
    String uiActionId,
    Instant clientOccurredAt,

    @NotNull
    Boolean enabled,

    @NotBlank
    String reason
) {
}
