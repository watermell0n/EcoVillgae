package com.b201.ecovillage.investment.application;

import java.util.UUID;

/**
 * 하루를 넘긴 결과다. 정산이 함께 일어났으면 몇 명에게 얼마를 지급했는지 담는다.
 */
public record InvestmentDayResult(
    UUID marketId,
    String marketStatus,
    String investmentMode,
    int currentStep,
    int totalSteps,
    boolean settled,
    int settledPositionCount,
    long settledAmount,
    long stateVersion
) {
}
