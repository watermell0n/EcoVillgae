package com.b201.ecovillage.finance.application;

import java.time.Instant;
import java.util.UUID;

public record DepositAccountResult(
    UUID accountId,
    String status,
    long principalAmount,
    long expectedInterestAmount,
    Instant maturesAt,
    long stateVersion
) {
}
