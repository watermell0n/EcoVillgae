package com.b201.ecovillage.finance.api;

import java.util.List;

public record DepositAndSavingsResponse(
    List<DepositAccountViewResponse> depositAccounts,
    List<SavingsAccountViewResponse> savingsAccounts
) {
}
