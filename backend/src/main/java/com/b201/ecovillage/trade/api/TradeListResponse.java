package com.b201.ecovillage.trade.api;

import java.util.List;

import com.b201.ecovillage.trade.application.PendingTradeView;

public record TradeListResponse(List<PendingTradeView> trades) {
}
