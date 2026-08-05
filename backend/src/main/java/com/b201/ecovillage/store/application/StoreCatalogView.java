package com.b201.ecovillage.store.application;

import java.util.UUID;

public record StoreCatalogView(
    UUID resourceId,
    String label,
    String status,
    int version,
    long stateVersion,
    long unitPrice,
    int vatRateBps,
    int stockQuantity,
    String productKind,
    String characterCode,
    /** 상점 상세 카드에 그대로 보여 주는 상품 설명. 없으면 null. */
    String description,
    /** 상점 기본 노출 순서. 캐릭터 상품만 값을 가지며 그 외에는 null. */
    Integer sortOrder
) {
}
