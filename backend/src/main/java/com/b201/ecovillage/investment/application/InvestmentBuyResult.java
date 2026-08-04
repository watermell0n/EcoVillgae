package com.b201.ecovillage.investment.application;

import java.util.UUID;

public record InvestmentBuyResult(
    UUID orderId,
    String status,
    int quantity,
    long executedPrice,
    int priceVersion,
    long totalAmount,
    int positionQuantity,
    long averagePrice,
    long stateVersion
) {
}
