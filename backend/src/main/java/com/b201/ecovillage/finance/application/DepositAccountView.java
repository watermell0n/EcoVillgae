package com.b201.ecovillage.finance.application;

import java.time.Instant;
import java.util.UUID;

public record DepositAccountView(
    UUID resourceId,
    String label,
    String status,
    int version,
    long stateVersion,
    long principalAmount,
    long expectedInterestAmount,
    long maturityAmount,
    Instant openedAt,
    Instant maturesAt,
    Instant closedAt
) {
}
