package com.b201.ecovillage.store.api;

import java.util.UUID;

public record StoreCatalogProductResponse(
    UUID resourceId,
    String label,
    String status,
    int version,
    long stateVersion,
    long unitPrice,
    String productKind,
    String characterCode
) {
}
