package com.b201.ecovillage.store.application;

import java.util.UUID;

public record StoreProductCommandResult(
    UUID productId,
    String status,
    long stateVersion,
    int version
) {
}
