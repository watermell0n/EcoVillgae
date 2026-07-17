package com.b201.bibim.domain.settlement;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialSettlementAllocationRepository
        extends JpaRepository<FinancialSettlementAllocationEntity, UUID> {

    List<FinancialSettlementAllocationEntity> findAllBySettlementItemId(UUID settlementItemId);
}
