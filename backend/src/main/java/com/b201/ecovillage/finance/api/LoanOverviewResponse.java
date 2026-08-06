package com.b201.ecovillage.finance.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LoanOverviewResponse(
    int creditScore,
    String creditLevel,
    long availableLimit,
    int interestRateBps,
    int termUnits,
    int installmentCount,
    boolean canApply,
    UUID pendingApplicationId,
    String pendingApplicationStatus,
    List<LoanResponse> loans,
    List<CreditEventResponse> creditEvents
) {
    public record LoanResponse(
        UUID loanId,
        long principalAmount,
        long outstandingPrincipal,
        long outstandingInterest,
        int appliedInterestRateBps,
        String status,
        Instant maturesAt,
        long stateVersion,
        List<LoanInstallmentResponse> installments
    ) { }
}
