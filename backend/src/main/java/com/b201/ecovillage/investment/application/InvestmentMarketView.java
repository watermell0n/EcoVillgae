package com.b201.ecovillage.investment.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvestmentMarketView(
    UUID marketId,
    UUID scenarioVersionId,
    String status,
    String investmentMode,
    String scenarioSource,
    String settlementRule,
    int currentStep,
    long stateVersion,
    boolean teacherView,
    List<Asset> assets
) {
    public record Asset(
        UUID assetId,
        String symbol,
        String name,
        String companyProfile,
        Long currentPrice,
        Integer priceVersion,
        List<PricePoint> priceHistory,
        List<News> news
    ) {
    }

    public record PricePoint(int step, long price, int changeRateBps, Instant revealedAt) {
    }

    public record News(
        UUID eventId,
        String title,
        String content,
        String status,
        long stateVersion,
        Instant revealedAt
    ) {
    }
}
