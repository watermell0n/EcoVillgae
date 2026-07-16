package com.b201.bibim.feature.shop;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class ShopDto {

    private ShopDto() {
    }

    public record ProductResponse(
            UUID productId,
            String name,
            String description,
            String imageUrl,
            long originalPrice,
            long price,
            int initialStock,
            int currentStock,
            String status,
            int displayOrder
    ) {
    }

    public record ProductListResponse(
            long walletBalance,
            List<ProductResponse> products
    ) {
    }

    public record PurchaseRequest(
            @NotNull UUID productId,
            @Min(1) @Max(99) int quantity
    ) {
    }

    public record PurchaseResponse(
            UUID purchaseId,
            UUID productId,
            String productName,
            int quantity,
            long unitPrice,
            long totalPrice,
            long walletBalance,
            int remainingStock,
            String productStatus,
            Instant purchasedAt,
            boolean replayed
    ) {
    }

    public record PurchaseHistoryItemResponse(
            UUID purchaseId,
            UUID productId,
            String productName,
            int quantity,
            long unitPrice,
            long totalPrice,
            String status,
            Instant purchasedAt
    ) {
    }

    public record PurchaseHistoryResponse(
            List<PurchaseHistoryItemResponse> purchases
    ) {
    }

    public record TeacherPurchaseResponse(
            UUID purchaseId,
            int participantNumber,
            String nickname,
            String productName,
            int quantity,
            long totalPrice,
            Instant purchasedAt
    ) {
    }

    public record TeacherShopSummaryResponse(
            UUID roomId,
            long totalPurchaseCount,
            long totalRevenue,
            List<ProductResponse> products,
            List<TeacherPurchaseResponse> recentPurchases
    ) {
    }
}
