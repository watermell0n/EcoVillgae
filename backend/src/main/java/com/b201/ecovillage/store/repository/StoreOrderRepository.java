package com.b201.ecovillage.store.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.store.domain.StoreOrder;

public interface StoreOrderRepository extends JpaRepository<StoreOrder, UUID> {

    Optional<StoreOrder> findByClassroomIdAndId(UUID classroomId, UUID id);

    Optional<StoreOrder> findByClassroomIdAndTransactionGroupId(
        UUID classroomId,
        UUID transactionGroupId
    );
}
