package com.b201.ecovillage.trade.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.trade.domain.TradeItemLeg;

public interface TradeItemLegRepository extends JpaRepository<TradeItemLeg, UUID> {

    List<TradeItemLeg> findAllByTradeIdOrderByInventoryItemId(UUID tradeId);
}
