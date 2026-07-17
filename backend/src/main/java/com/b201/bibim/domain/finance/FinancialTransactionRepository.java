package com.b201.bibim.domain.finance;

import java.util.UUID;
import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialTransactionRepository
        extends JpaRepository<FinancialTransactionEntity, UUID> {

    Optional<FinancialTransactionEntity> findByIdempotencyKey(String idempotencyKey);

    List<FinancialTransactionEntity> findAllByStudentIdOrderByCreatedAtDesc(UUID studentId);

    boolean existsByRoomIdAndStatus(UUID roomId, String status);
}
