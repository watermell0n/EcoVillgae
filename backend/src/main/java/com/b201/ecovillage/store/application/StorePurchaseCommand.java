package com.b201.ecovillage.store.application;

import java.util.UUID;

public record StorePurchaseCommand(
    String studentSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    UUID productId,
    int quantity,
    int expectedProductVersion,
    Long expectedStateVersion
) {
}
