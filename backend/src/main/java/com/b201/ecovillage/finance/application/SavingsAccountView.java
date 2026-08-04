package com.b201.ecovillage.finance.application;

import java.time.Instant;
import java.util.UUID;

public record SavingsAccountView(
    UUID resourceId,
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
