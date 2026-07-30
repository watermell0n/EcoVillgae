package com.b201.ecovillage.trade.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.trade.domain.TradeDecision;

public interface TradeDecisionRepository extends JpaRepository<TradeDecision, UUID> {

    List<TradeDecision> findAllByTradeIdOrderByDecidedAt(UUID tradeId);
}
