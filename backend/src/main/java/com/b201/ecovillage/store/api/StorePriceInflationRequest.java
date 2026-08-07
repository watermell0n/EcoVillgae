package com.b201.ecovillage.store.api;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 학급의 모든 상점 상품 가격을 같은 비율로 올리는 요청이다.
 *
 * @param rateBps 인상률(bp). 1000이면 10%다.
 */
public record StorePriceInflationRequest(
    @Size(max = 120) String screenId,
    @Size(max = 120) String uiActionId,
    Instant clientOccurredAt,

    @NotNull @Min(0) Long expectedStateVersion,
    @NotNull @Min(0) Integer expectedPolicyVersion,

    @NotNull @Min(1) @Max(100_000) Integer rateBps
) {
}
