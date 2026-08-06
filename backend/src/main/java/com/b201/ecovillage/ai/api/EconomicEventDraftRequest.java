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
    String teacherContext,

    /**
     * INVESTMENT_NEWS에서 이야기를 붙일 투자 방식이다. 학급이 단기와 장기를 함께 운영하므로
     * 교사가 어느 쪽을 만드는지 지정한다. 비우면 학급이 쓰는 방식 전체를 대상으로 삼는다.
     */
    String investmentMode
) {
}
