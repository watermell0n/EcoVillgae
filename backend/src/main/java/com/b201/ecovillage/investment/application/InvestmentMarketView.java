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
        /** 이 종목이 하루에 오르내릴 수 있는 범위다. 학생이 위험을 가늠할 수 있게 공개한다. */
        int minReturnBps,
        int maxReturnBps,
        Long currentPrice,
        Integer priceVersion,
        List<PricePoint> priceHistory,
        /**
         * 아직 공개되지 않은 가격 경로다. 교사가 초안 시나리오를 검토하고 승인하거나 다시
         * 생성할지 정하려면 앞으로의 흐름을 봐야 한다.
         *
         * <p>학생에게는 어떤 경우에도 채우지 않는다. 미래 가격이 새면 투자 활동 자체가
         * 무너진다. 교사에게도 시장이 초안일 때만 채운다.
         */
        List<PricePoint> plannedPriceHistory,
        List<News> news,
        int positionQuantity,
        long averagePrice,
        long currentValue,
        long totalBoughtAmount,
        long totalSoldAmount,
        long profitLoss
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
