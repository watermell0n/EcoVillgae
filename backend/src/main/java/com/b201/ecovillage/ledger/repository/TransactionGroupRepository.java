package com.b201.ecovillage.ledger.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.b201.ecovillage.ledger.domain.TransactionGroup;

public interface TransactionGroupRepository extends Repository<TransactionGroup, UUID> {

    TransactionGroup save(TransactionGroup transactionGroup);

    TransactionGroup saveAndFlush(TransactionGroup transactionGroup);

    Optional<TransactionGroup> findById(UUID id);

    List<TransactionGroup> findAllByIdIn(Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select transactionGroup
          from TransactionGroup transactionGroup
         where transactionGroup.classroomId = :classroomId
           and transactionGroup.id = :transactionGroupId
        """)
    Optional<TransactionGroup> findForRecovery(
        @Param("classroomId") UUID classroomId,
        @Param("transactionGroupId") UUID transactionGroupId
    );

    boolean existsByRecoveryOfGroupId(UUID recoveryOfGroupId);

    List<TransactionGroup> findAllByRecoveryOfGroupIdOrderByCreatedAt(UUID recoveryOfGroupId);
}
