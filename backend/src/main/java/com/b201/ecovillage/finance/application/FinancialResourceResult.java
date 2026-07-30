package com.b201.ecovillage.finance.application;

import java.util.UUID;

public record FinancialResourceResult(
    UUID financialResourceId,
    String status,
    long stateVersion
) {
}
