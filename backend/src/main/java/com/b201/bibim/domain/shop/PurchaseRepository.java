package com.b201.bibim.domain.shop;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRepository extends JpaRepository<PurchaseEntity, UUID> {

    Optional<PurchaseEntity> findByTransactionId(UUID transactionId);

    List<PurchaseEntity> findAllByStudentIdOrderByCreatedAtDesc(UUID studentId);

    List<PurchaseEntity> findAllByRoomIdOrderByCreatedAtDesc(UUID roomId);

    long countByRoomId(UUID roomId);

    long countByStudentId(UUID studentId);
}
