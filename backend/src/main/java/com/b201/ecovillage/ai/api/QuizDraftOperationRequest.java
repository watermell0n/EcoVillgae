package com.b201.ecovillage.ai.api;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * API-QZ-002 입력 계약이다. 계약에 선언하지 않은 최상위 필드는 받지 않는다.
 */
public record QuizDraftOperationRequest(
    Long expectedStateVersion,
    String screenId,
    String uiActionId,
    Instant clientOccurredAt,

    @NotNull
    @Min(4)
    @Max(6)
    Integer grade,

    @NotBlank
    String topic,

    @NotBlank
    String learningObjective,

    @NotBlank
    String contentType,

    @NotBlank
    String questionType,

    @NotNull
    @Min(1)
    @Max(30)
    Integer questionCount
) {
}
