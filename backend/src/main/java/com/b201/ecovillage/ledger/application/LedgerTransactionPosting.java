package com.b201.ecovillage.ledger.application;

import java.util.Objects;
import java.util.UUID;

public record LedgerTransactionPosting(UUID ledgerAccountId, long amount) {

    public LedgerTransactionPosting {
        Objects.requireNonNull(ledgerAccountId, "ledgerAccountId must not be null");
        if (amount == 0) {
            throw new IllegalArgumentException("posting amount must not be zero");
        }
    }
}
