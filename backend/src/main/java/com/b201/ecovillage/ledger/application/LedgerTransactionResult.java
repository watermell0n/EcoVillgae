package com.b201.ecovillage.ledger.application;

import java.util.UUID;

import com.b201.ecovillage.ledger.domain.TransactionGroupStatus;

public record LedgerTransactionResult(
    UUID transactionGroupId,
    TransactionGroupStatus status,
    long stateVersion,
    boolean replayed
) {
}
