package com.b201.ecovillage.trade.application;

import java.util.UUID;

public record TradeCommandResult(
    UUID tradeId,
    String status,
    long stateVersion
) {
}
