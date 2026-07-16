package com.b201.bibim.domain.fund;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FundCostOptionRepository extends JpaRepository<FundCostOptionEntity, UUID> {

    List<FundCostOptionEntity> findAllByRoomIdOrderByDisplayOrder(UUID roomId);
}

