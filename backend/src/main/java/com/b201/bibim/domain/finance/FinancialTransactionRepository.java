package com.b201.bibim.domain.finance;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialTransactionRepository
        extends JpaRepository<FinancialTransactionEntity, UUID> {
}
