package com.b201.ecovillage.store.application;

import java.util.UUID;

/**
 * 학급의 모든 상점 상품 가격을 같은 비율로 올린다.
 *
 * @param rateBps 인상률(bp). 1000이면 10%다. 0보다 커야 한다.
 */
public record StorePriceInflationCommand(
    String teacherSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    long expectedStateVersion,
    int expectedPolicyVersion,
    int rateBps
) {
}
