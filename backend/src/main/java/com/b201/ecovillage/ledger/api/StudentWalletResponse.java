package com.b201.ecovillage.ledger.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudentWalletResponse(
    long balance,
    List<RecentTransactionResponse> recentTransactions
) {
    public record RecentTransactionResponse(
        UUID resourceId,
        String label,
        String status,
        int version,
        long stateVersion,
        long amount,
        Instant occurredAt
    ) {
    }
}
