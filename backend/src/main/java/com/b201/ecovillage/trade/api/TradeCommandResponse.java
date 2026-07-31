package com.b201.ecovillage.trade.api;

import java.util.UUID;

public record TradeCommandResponse(
    UUID tradeId,
    String status,
    long stateVersion
) {
}
