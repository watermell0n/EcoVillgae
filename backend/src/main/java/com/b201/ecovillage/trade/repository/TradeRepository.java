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

    /**
     * 교사 승인을 기다리는 거래다. 승인·정산 화면이 이 목록을 그대로 보여 준다.
     */
    List<Trade> findAllByClassroomIdAndStatusOrderByCreatedAtDesc(
        UUID classroomId,
        com.b201.ecovillage.trade.domain.TradeStatus status
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
