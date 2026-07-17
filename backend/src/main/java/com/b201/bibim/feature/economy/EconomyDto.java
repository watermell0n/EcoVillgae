package com.b201.bibim.feature.economy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EconomyDto {

    private EconomyDto() {
    }

    public record OverviewResponse(
            long walletBalance,
            long depositBalance,
            long investmentBalance,
            long purchaseCount,
            String depositStatus,
            String investmentStatus,
            long totalAssets
    ) {
    }

    public record TransactionItemResponse(
            UUID transactionId,
            String transactionType,
            String status,
            String description,
            long walletAmount,
            Map<String, Object> metadata,
            Instant createdAt
    ) {
    }

    public record TransactionHistoryResponse(
            List<TransactionItemResponse> transactions
    ) {
    }
}
