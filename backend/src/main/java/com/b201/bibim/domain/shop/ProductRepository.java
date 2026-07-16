package com.b201.bibim.domain.shop;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {

    List<ProductEntity> findAllByRoomIdOrderByDisplayOrder(UUID roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from ProductEntity product where product.id = :productId")
    Optional<ProductEntity> findByIdForUpdate(@Param("productId") UUID productId);
}
