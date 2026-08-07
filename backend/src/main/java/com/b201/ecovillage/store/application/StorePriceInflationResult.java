package com.b201.ecovillage.store.application;

/**
 * @param appliedCount 가격이 실제로 오른 상품 수
 * @param clampedCount 정책 상한에 걸려 상한값으로 맞춘 상품 수
 * @param unchangedCount 올림 결과가 원래 가격과 같아 새 버전을 만들지 않은 상품 수
 */
public record StorePriceInflationResult(
    int appliedCount,
    int clampedCount,
    int unchangedCount,
    long stateVersion
) {

    public int totalCount() {
        return appliedCount + unchangedCount;
    }
}
