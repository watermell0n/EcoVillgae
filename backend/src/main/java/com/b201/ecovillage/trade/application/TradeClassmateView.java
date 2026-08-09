package com.b201.ecovillage.trade.application;

import java.util.UUID;

public record TradeClassmateView(
    UUID studentId,
    int rosterNumber,
    String nickname
) {
}
