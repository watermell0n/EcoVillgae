package com.b201.bibim.domain.settlement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementItemRepository extends JpaRepository<SettlementItemEntity, UUID> {

    List<SettlementItemEntity> findAllBySettlementRunId(UUID settlementRunId);

    Optional<SettlementItemEntity> findBySettlementRunIdAndStudentId(
            UUID settlementRunId,
            UUID studentId
    );
}
