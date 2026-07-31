package com.b201.ecovillage.ledger.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.ledger.domain.TransactionBusinessRef;

public interface TransactionBusinessRefRepository
    extends JpaRepository<TransactionBusinessRef, UUID> {

    Optional<TransactionBusinessRef>
        findTopByClassroomIdAndRefTypeAndRefIdOrderByCreatedAtDesc(
            UUID classroomId,
            String refType,
            UUID refId
        );
}
