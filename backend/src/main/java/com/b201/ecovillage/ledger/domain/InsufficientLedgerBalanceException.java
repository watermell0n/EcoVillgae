package com.b201.ecovillage.ledger.domain;

import java.util.UUID;

public class InsufficientLedgerBalanceException extends RuntimeException {

    private final UUID ledgerAccountId;
    private final long availableBalance;
    private final long requestedAmount;

    public InsufficientLedgerBalanceException(
        UUID ledgerAccountId,
        long availableBalance,
        long requestedAmount
    ) {
        super("insufficient balance for ledger account " + ledgerAccountId);
        this.ledgerAccountId = ledgerAccountId;
        this.availableBalance = availableBalance;
        this.requestedAmount = requestedAmount;
    }

    public UUID getLedgerAccountId() {
        return ledgerAccountId;
    }

    public long getAvailableBalance() {
        return availableBalance;
    }

    public long getRequestedAmount() {
        return requestedAmount;
    }
}
