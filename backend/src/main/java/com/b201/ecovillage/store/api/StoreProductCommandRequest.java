package com.b201.ecovillage.store.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.b201.ecovillage.store.application.StoreProductCommandType;
import com.b201.ecovillage.store.domain.StoreProductKind;

public record StoreProductCommandRequest(
    @NotNull Long expectedStateVersion,
    @NotNull Integer expectedPolicyVersion,
    @Size(max = 120) String screenId,
    @Size(max = 120) String uiActionId,
    Instant clientOccurredAt,
    @NotNull StoreProductCommandType command,
    UUID productId,
    @NotBlank @Size(max = 120) String name,
    @Size(max = 2_000) String description,
    @NotNull StoreProductKind productKind,
    @NotNull Long unitPrice,
    @NotNull Integer vatRateBps,
    @NotNull Integer stockQuantity,
    UUID characterSourceId,
    Integer expectedProductVersion,
    @Size(max = 2_000_000) String imageUrl
) {
}
