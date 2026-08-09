package com.b201.ecovillage.investment.application;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.b201.ecovillage.investment.domain.InvestmentMarket;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;

/** 투자 시장 변경을 교사와 학생의 열린 화면에 전달한다. */
@Service
public class InvestmentRealtimePublisher {

    private static final String STREAM = "INVESTMENT_MARKET";
    private static final String EVENT_TYPE = "investment.market.status_changed";

    private final RealtimeEventPublisher eventPublisher;

    public InvestmentRealtimePublisher(RealtimeEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publish(UUID classroomId, InvestmentMarket market, String reason) {
        eventPublisher.publish(
            classroomId,
            STREAM,
            null,
            EVENT_TYPE,
            "INVESTMENT_MARKET",
            market.getId(),
            market.getStateVersion(),
            Map.of(
                "market_id", market.getId().toString(),
                "investment_mode", market.getInvestmentMode().name(),
                "status", market.getStatus(),
                "current_step", market.getCurrentStep(),
                "price_version", market.getCurrentStep(),
                "reason", reason
            ),
            true
        );
    }
}
