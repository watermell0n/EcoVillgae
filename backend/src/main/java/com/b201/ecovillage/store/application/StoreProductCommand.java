package com.b201.ecovillage.store.application;

import java.util.UUID;

import com.b201.ecovillage.store.domain.StoreProductKind;

public record StoreProductCommand(
    String teacherSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    long expectedStateVersion,
    int expectedPolicyVersion,
    StoreProductCommandType command,
    UUID productId,
    String name,
    String description,
    StoreProductKind productKind,
    long unitPrice,
    int vatRateBps,
    int stockQuantity,
    UUID characterSourceId,
    Integer expectedProductVersion,
    String imageUrl
) {
}
