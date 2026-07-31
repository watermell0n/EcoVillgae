package com.b201.ecovillage.store.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.store.domain.StoreOrderItem;

public interface StoreOrderItemRepository extends JpaRepository<StoreOrderItem, UUID> {

    List<StoreOrderItem> findAllByStoreOrderId(UUID storeOrderId);
}
