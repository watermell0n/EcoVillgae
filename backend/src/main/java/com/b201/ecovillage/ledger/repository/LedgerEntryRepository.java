package com.b201.ecovillage.ledger.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import com.b201.ecovillage.ledger.domain.LedgerEntry;

public interface LedgerEntryRepository extends Repository<LedgerEntry, UUID> {

    <S extends LedgerEntry> List<S> saveAll(Iterable<S> entries);

    List<LedgerEntry> findAllByTransactionGroupIdOrderByLedgerAccountId(UUID transactionGroupId);

    List<LedgerEntry> findTop20ByLedgerAccountIdOrderByAccountSequenceDesc(UUID ledgerAccountId);
}
