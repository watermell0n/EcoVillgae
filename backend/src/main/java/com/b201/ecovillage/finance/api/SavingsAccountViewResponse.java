package com.b201.ecovillage.finance.api;

import java.time.Instant;
import java.util.UUID;

public record SavingsAccountViewResponse(
    UUID resourceId,
    UUID productId,
    String label,
    String status,
    int installmentCount,
    int paidInstallmentCount,
    long installmentAmount,
    long principalBalance,
    long expectedInterestAmount,
    Instant openedAt,
    Instant maturesAt,
    long stateVersion
) { }
