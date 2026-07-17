package com.b201.bibim.domain.fund;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FundExpenseRepository extends JpaRepository<FundExpenseEntity, UUID> {

    Optional<FundExpenseEntity> findByRoomId(UUID roomId);

    Optional<FundExpenseEntity> findByTransactionId(UUID transactionId);

    boolean existsByRoomId(UUID roomId);
}

