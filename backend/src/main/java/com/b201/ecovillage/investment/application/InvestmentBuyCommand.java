package com.b201.ecovillage.investment.application;

import java.util.UUID;

public record InvestmentBuyCommand(
    String studentSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    UUID assetId,
    int quantity,
    int expectedPriceVersion
) {
}
