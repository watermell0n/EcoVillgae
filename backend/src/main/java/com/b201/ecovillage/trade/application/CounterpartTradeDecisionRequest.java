package com.b201.ecovillage.trade.application;

import java.util.UUID;

public record CounterpartTradeDecisionRequest(
    String studentSubject,
    UUID classroomId,
    UUID tradeId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    CounterpartTradeDecision decision,
    String reason
) {
}
