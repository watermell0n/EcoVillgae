package com.b201.bibim.domain.finance;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialEntryRepository extends JpaRepository<FinancialEntryEntity, UUID> {

    List<FinancialEntryEntity> findAllByTransactionIdOrderByEntryOrder(UUID transactionId);
}
