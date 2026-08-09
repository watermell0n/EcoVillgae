package com.b201.ecovillage.investment.application;

import java.time.Instant;
import java.util.UUID;

public record InvestmentOrderHistoryView(
    UUID orderId,
    UUID assetId,
    String symbol,
    String assetName,
    String investmentMode,
    String side,
    int quantity,
    long executedPrice,
    long totalAmount,
    int executedPriceStep,
    String status,
    Instant executedAt
) {
}
