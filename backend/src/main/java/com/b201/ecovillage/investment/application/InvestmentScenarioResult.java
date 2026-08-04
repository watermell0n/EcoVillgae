package com.b201.ecovillage.investment.application;

import java.util.List;
import java.util.UUID;

public record InvestmentScenarioResult(
    UUID marketId,
    UUID scenarioVersionId,
    String marketStatus,
    String investmentMode,
    String scenarioSource,
    String settlementRule,
    int stepCount,
    long stateVersion,
    List<Asset> assets
) {
    public record Asset(UUID assetId, String symbol, String name, long initialPrice) {
    }
}
