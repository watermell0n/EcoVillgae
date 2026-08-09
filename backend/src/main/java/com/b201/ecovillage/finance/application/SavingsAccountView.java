package com.b201.ecovillage.finance.application;

import java.time.Instant;
import java.util.UUID;

public record SavingsAccountView(
    UUID resourceId,
    UUID productId,
    String label,
    String status,
    int installmentCount,
    int paidInstallmentCount,
    long installmentAmount,
    long principalBalance,
    long expectedInterestAmount,
    int interestRateBps,
    Instant openedAt,
    Instant maturesAt,
    Instant nextInstallmentAt,
    String nextInstallmentStatus,
    long stateVersion
) { }
