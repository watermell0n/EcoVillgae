package com.b201.ecovillage.trade.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.trade.domain.TradeMoneyLeg;

public interface TradeMoneyLegRepository extends JpaRepository<TradeMoneyLeg, UUID> {

    List<TradeMoneyLeg> findAllByTradeIdOrderByFromStudentId(UUID tradeId);
}
