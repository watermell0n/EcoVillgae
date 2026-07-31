package com.b201.ecovillage.finance.api;

import java.time.Instant;
import java.util.UUID;

public record DepositAccountViewResponse(
    UUID resourceId,
    String label,
    String status,
    int version,
    long stateVersion,
    long principalAmount,
    Instant maturesAt
) {
}
