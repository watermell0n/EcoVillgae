package com.b201.ecovillage.quiz.api;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 넌센스 문제은행 가져오기 입력이다.
 * 넌센스는 학습목표를 평가하지 않으므로 주제와 학습목표를 받지 않는다.
 */
public record NonsenseImportRequest(
    String screenId,
    String uiActionId,
    Instant clientOccurredAt,

    @NotNull
    @Min(4)
    @Max(6)
    Integer grade,

    @NotNull
    @Min(1)
    @Max(30)
    Integer questionCount
) {
}
