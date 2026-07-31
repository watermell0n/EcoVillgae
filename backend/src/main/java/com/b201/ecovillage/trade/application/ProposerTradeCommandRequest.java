package com.b201.ecovillage.trade.application;

import java.util.UUID;

public record ProposerTradeCommandRequest(
    String studentSubject,
    UUID classroomId,
    UUID tradeId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    ProposerTradeCommand command
) {
}
