package com.b201.ecovillage.store.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.b201.ecovillage.store.domain.StoreStock;

public interface StoreStockRepository extends JpaRepository<StoreStock, UUID> {

    Optional<StoreStock> findByClassroomIdAndStoreProductId(
        UUID classroomId,
        UUID storeProductId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select stock
          from StoreStock stock
         where stock.classroomId = :classroomId
           and stock.storeProductId = :storeProductId
        """)
    Optional<StoreStock> findForUpdate(
        @Param("classroomId") UUID classroomId,
        @Param("storeProductId") UUID storeProductId
    );
}
