package com.b201.ecovillage.investment.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * AI 투자 뉴스 한 건이 필요한 입력을 모아둔 조회 결과다.
 *
 * <p>{@code publicPriceFacts}는 1단계부터 {@code currentStepIndex}까지 빠짐없이 오름차순으로
 * 담긴다. 뉴스가 학생이 이미 본 시세와 어긋나지 않게 하려면 현재 단계만으로는 부족하다.
 */
public record InvestmentNewsTargetView(
    UUID scenarioVersionId,
    UUID assetId,
    UUID priceStepId,
    String assetName,
    String assetSymbol,
    int currentStepIndex,
    List<PriceFact> publicPriceFacts
) {

    public InvestmentNewsTargetView {
        publicPriceFacts = List.copyOf(publicPriceFacts);
    }

    public record PriceFact(
        int stepIndex,
        long price,
        int changeRateBps,
        Instant revealedAt
    ) {
    }
}
