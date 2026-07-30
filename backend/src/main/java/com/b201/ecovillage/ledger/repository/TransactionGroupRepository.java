package com.b201.ecovillage.ledger.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import com.b201.ecovillage.ledger.domain.TransactionGroup;

public interface TransactionGroupRepository extends Repository<TransactionGroup, UUID> {

    TransactionGroup save(TransactionGroup transactionGroup);

    Optional<TransactionGroup> findById(UUID id);

    List<TransactionGroup> findAllByRecoveryOfGroupIdOrderByCreatedAt(UUID recoveryOfGroupId);
}
