package com.b201.ecovillage.finance.api;

public record LoanPolicyResponse(
    int version,
    long maximumPrincipal,
    int interestRateBps,
    int termUnits,
    int installmentCount,
    int graceUnits,
    int onTimeCredit,
    int overdueCredit,
    int repaidCredit
) { }
