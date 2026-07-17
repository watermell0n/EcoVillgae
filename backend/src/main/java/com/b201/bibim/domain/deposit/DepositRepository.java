package com.b201.bibim.domain.deposit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepositRepository extends JpaRepository<DepositEntity, UUID> {

    Optional<DepositEntity> findFirstBySessionIdAndStudentIdAndStatusOrderByUpdatedAtDesc(
            UUID sessionId,
            UUID studentId,
            String status
    );

    List<DepositEntity> findAllBySessionIdAndStudentIdAndStatus(
            UUID sessionId,
            UUID studentId,
            String status
    );

    boolean existsBySessionIdAndStudentIdAndStatus(
            UUID sessionId,
            UUID studentId,
            String status
    );

    Optional<DepositEntity> findByTransactionId(UUID transactionId);

    List<DepositEntity> findAllByRoomId(UUID roomId);

    long countByRoomIdAndStatus(UUID roomId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select deposit from DepositEntity deposit
            where deposit.sessionId = :sessionId
              and deposit.studentId = :studentId
              and deposit.status = 'DRAFT'
            """)
    Optional<DepositEntity> findDraftForUpdate(
            @Param("sessionId") UUID sessionId,
            @Param("studentId") UUID studentId
    );
}
