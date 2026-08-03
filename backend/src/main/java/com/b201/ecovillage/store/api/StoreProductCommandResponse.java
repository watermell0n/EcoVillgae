package com.b201.ecovillage.store.api;

import java.util.UUID;

public record StoreProductCommandResponse(
    UUID productId,
    String status,
    long stateVersion,
    int version
) {
}
