package com.b201.ecovillage.ledger.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudentWalletView(
    long balance,
    List<RecentTransaction> recentTransactions
) {
    public record RecentTransaction(
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
