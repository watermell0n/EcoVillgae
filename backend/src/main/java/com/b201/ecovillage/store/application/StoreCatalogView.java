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
    String characterCode
) {
}
