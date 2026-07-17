package com.b201.bibim.domain.investment;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentAllocationRepository
        extends JpaRepository<InvestmentAllocationEntity, UUID> {

    List<InvestmentAllocationEntity> findAllByOrderId(UUID orderId);

    void deleteAllByOrderId(UUID orderId);
}
