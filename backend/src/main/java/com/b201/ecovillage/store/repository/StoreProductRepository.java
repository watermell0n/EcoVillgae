package com.b201.ecovillage.store.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.store.domain.StoreProduct;
import com.b201.ecovillage.store.domain.StoreProductKind;
import com.b201.ecovillage.store.domain.StoreProductStatus;

public interface StoreProductRepository extends JpaRepository<StoreProduct, UUID> {

    Optional<StoreProduct> findByClassroomIdAndId(UUID classroomId, UUID id);

    List<StoreProduct> findAllByClassroomIdAndStatusAndProductKindOrderByName(
        UUID classroomId,
        StoreProductStatus status,
        StoreProductKind productKind
    );
}
