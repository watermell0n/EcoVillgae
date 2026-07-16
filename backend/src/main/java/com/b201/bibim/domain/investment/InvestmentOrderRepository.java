package com.b201.bibim.domain.investment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvestmentOrderRepository extends JpaRepository<InvestmentOrderEntity, UUID> {

    Optional<InvestmentOrderEntity> findBySessionIdAndStudentId(UUID sessionId, UUID studentId);

    Optional<InvestmentOrderEntity> findByTransactionId(UUID transactionId);

    List<InvestmentOrderEntity> findAllByRoomId(UUID roomId);

    long countByRoomIdAndStatus(UUID roomId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select orders from InvestmentOrderEntity orders where orders.sessionId = :sessionId and orders.studentId = :studentId")
    Optional<InvestmentOrderEntity> findForUpdate(
            @Param("sessionId") UUID sessionId,
            @Param("studentId") UUID studentId
    );
}
