package com.b201.ecovillage.trade.repository;

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
