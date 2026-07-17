package com.b201.bibim.domain.settlement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialSettlementItemRepository
        extends JpaRepository<FinancialSettlementItemEntity, UUID> {

    List<FinancialSettlementItemEntity> findAllBySettlementRunId(UUID settlementRunId);

    Optional<FinancialSettlementItemEntity> findBySettlementRunIdAndStudentId(
            UUID settlementRunId,
            UUID studentId
    );
}
