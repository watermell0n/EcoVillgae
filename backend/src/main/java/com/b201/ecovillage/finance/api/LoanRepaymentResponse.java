package com.b201.ecovillage.finance.api;

import java.util.UUID;

public record LoanRepaymentResponse(
    UUID financialResourceId,
    String status,
    long stateVersion,
    long remainingPrincipal,
    long remainingInterest,
    int creditScore,
    String creditLevel,
    boolean replayed
) { }
