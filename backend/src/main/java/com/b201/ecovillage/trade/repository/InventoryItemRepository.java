package com.b201.ecovillage.trade.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.b201.ecovillage.trade.domain.InventoryItem;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    List<InventoryItem> findAllByClassroomIdAndClassroomStudentIdOrderByAcquiredAtDesc(
        UUID classroomId,
        UUID classroomStudentId
    );

    boolean existsByClassroomIdAndClassroomStudentIdAndCharacterCatalogId(
        UUID classroomId,
        UUID classroomStudentId,
        UUID characterCatalogId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select item
          from InventoryItem item
         where item.classroomId = :classroomId
           and item.id = :itemId
        """)
    java.util.Optional<InventoryItem> findByIdForUpdate(
        @Param("classroomId") UUID classroomId,
        @Param("itemId") UUID itemId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select item
          from InventoryItem item
         where item.classroomId = :classroomId
           and item.classroomStudentId = :studentId
         order by item.id
        """)
    List<InventoryItem> findAllOwnedForUpdate(
        @Param("classroomId") UUID classroomId,
        @Param("studentId") UUID studentId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select item
          from InventoryItem item
         where item.classroomId = :classroomId
           and item.id in :itemIds
         order by item.id
        """)
    List<InventoryItem> findAllForUpdate(
        @Param("classroomId") UUID classroomId,
        @Param("itemIds") Collection<UUID> itemIds
    );
}
