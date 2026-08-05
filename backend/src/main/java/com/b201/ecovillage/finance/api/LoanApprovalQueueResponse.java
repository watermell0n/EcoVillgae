package com.b201.ecovillage.finance.api;

import java.util.List;

public record LoanApprovalQueueResponse(
    LoanPolicyResponse policy,
    List<LoanApplicationResponse> applications
) { }
