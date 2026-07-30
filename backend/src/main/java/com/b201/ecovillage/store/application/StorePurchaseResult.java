package com.b201.ecovillage.store.application;

import java.util.UUID;

public record StorePurchaseResult(
    UUID orderId,
    String status,
    long totalAmount,
    long stateVersion
) {
}
