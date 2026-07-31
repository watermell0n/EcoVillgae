package com.b201.ecovillage.finance.application;

import java.util.UUID;

import com.b201.ecovillage.finance.domain.FinancialProductType;

public record FinancialProductCommand(
    String teacherSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    FinancialProductCommandType command,
    UUID productId,
    FinancialProductType productType,
    String name,
    int termUnits,
    int interestRateBps,
    int earlyTerminationRateBps,
    Integer installmentCount,
    Long installmentAmount,
    long minimumAmount,
    long maximumAmount,
    Integer expectedProductVersion
) {
}
