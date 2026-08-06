package com.b201.ecovillage.finance.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LoanApprovalQueueResponse(
    LoanPolicyResponse policy,
    List<LoanApplicationResponse> applications,
    List<ActiveLoanResponse> activeLoans
) {
    public record ActiveLoanResponse(
        UUID loanId,
        UUID studentId,
        String studentNickname,
        long principalAmount,
        long outstandingPrincipal,
        long outstandingInterest,
        int appliedInterestRateBps,
        String status,
        Instant maturesAt,
        long stateVersion,
        int creditScore,
        String creditLevel,
        List<LoanInstallmentResponse> installments
    ) { }
}
