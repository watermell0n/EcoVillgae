package com.b201.ecovillage.store.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.store.domain.StoreProductVersion;

public interface StoreProductVersionRepository
    extends JpaRepository<StoreProductVersion, UUID> {

    Optional<StoreProductVersion>
        findTopByClassroomIdAndStoreProductIdAndRetiredAtIsNullOrderByVersionDesc(
            UUID classroomId,
            UUID storeProductId
        );

    Optional<StoreProductVersion>
        findTopByClassroomIdAndStoreProductIdAndRetiredAtIsNullAndPublishedAtLessThanEqualOrderByVersionDesc(
            UUID classroomId,
            UUID storeProductId,
            Instant publishedAt
        );

    Optional<StoreProductVersion> findByClassroomIdAndStoreProductIdAndVersion(
        UUID classroomId,
        UUID storeProductId,
        int version
    );
}
