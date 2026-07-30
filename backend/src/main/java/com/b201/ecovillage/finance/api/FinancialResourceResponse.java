package com.b201.ecovillage.finance.api;

import java.util.UUID;

public record FinancialResourceResponse(
    UUID financialResourceId,
    String status,
    long stateVersion
) {
}
