package com.b201.ecovillage.store.api;

import java.util.UUID;

public record StoreCatalogProductResponse(
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
    String description,
    Integer sortOrder,
    String imageUrl
) {
}
