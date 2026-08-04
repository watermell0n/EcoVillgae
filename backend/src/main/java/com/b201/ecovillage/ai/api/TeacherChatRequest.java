package com.b201.ecovillage.ai.api;

import java.time.Instant;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * API-AI-001 입력 계약이다. 계약에 선언하지 않은 최상위 필드는 받지 않는다.
 */
public record TeacherChatRequest(
    Long expectedStateVersion,
    String screenId,
    String uiActionId,
    Instant clientOccurredAt,

    @NotBlank
    @Size(max = 1000)
    String question,

    @NotNull
    @Valid
    PeriodRequest period,

    @NotNull
    Set<String> metricScopes,

    Boolean includePolicyHistory
) {

    public record PeriodRequest(
        @NotNull Instant from,
        @NotNull Instant to
    ) {
    }
}
