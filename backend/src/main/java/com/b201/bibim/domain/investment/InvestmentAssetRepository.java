package com.b201.bibim.domain.investment;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentAssetRepository extends JpaRepository<InvestmentAssetEntity, UUID> {

    List<InvestmentAssetEntity> findAllByRoomIdOrderByDisplayOrder(UUID roomId);

    long countByRoomId(UUID roomId);
}
