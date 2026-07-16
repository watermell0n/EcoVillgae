package com.b201.bibim.domain.settlement;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRunRepository extends JpaRepository<SettlementRunEntity, UUID> {

    Optional<SettlementRunEntity> findByRoomIdAndSettlementType(
            UUID roomId,
            String settlementType
    );

    Optional<SettlementRunEntity> findByIdempotencyKey(String idempotencyKey);

    boolean existsByRoomIdAndSettlementTypeAndStatus(
            UUID roomId,
            String settlementType,
            String status
    );
}
