package com.b201.ecovillage.ledger.domain;

public record LedgerAccountPosting(
    long accountSequence,
    long amount,
    long balanceBefore,
    long balanceAfter
) {
}
