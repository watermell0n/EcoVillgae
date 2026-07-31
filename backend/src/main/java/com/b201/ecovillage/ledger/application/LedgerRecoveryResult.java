package com.b201.ecovillage.ledger.application;

import java.util.UUID;

import com.b201.ecovillage.ledger.domain.TransactionGroupStatus;

public record LedgerRecoveryResult(
    UUID transactionGroupId,
    UUID recoveryOfGroupId,
    UUID rootTransactionGroupId,
    TransactionGroupStatus status,
    boolean replayed
) {
}
