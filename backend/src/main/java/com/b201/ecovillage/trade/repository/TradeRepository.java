package com.b201.ecovillage.trade.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.b201.ecovillage.trade.domain.Trade;

public interface TradeRepository extends JpaRepository<Trade, UUID> {

    Optional<Trade> findByClassroomIdAndId(UUID classroomId, UUID id);

    Optional<Trade> findByClassroomIdAndTransactionGroupId(
        UUID classroomId,
        UUID transactionGroupId
    );

    boolean existsByClassroomIdAndStatusIn(
        UUID classroomId,
        Collection<com.b201.ecovillage.trade.domain.TradeStatus> statuses
    );

    /** 교사가 처리한 거래다. 운영 기록이 최근 것부터 읽는다. */
    List<Trade> findAllByClassroomIdAndStatusInOrderByCreatedAtDesc(
        UUID classroomId,
        Collection<com.b201.ecovillage.trade.domain.TradeStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select trade
          from Trade trade
         where trade.classroomId = :classroomId
           and trade.id = :tradeId
        """)
    Optional<Trade> findForUpdate(
        @Param("classroomId") UUID classroomId,
        @Param("tradeId") UUID tradeId
    );
}
