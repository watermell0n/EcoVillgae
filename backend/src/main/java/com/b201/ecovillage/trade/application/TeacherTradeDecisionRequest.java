package com.b201.ecovillage.trade.application;

import java.util.UUID;

public record TeacherTradeDecisionRequest(
    String teacherSubject,
    UUID classroomId,
    UUID tradeId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    TeacherTradeDecision decision,
    String reason
) {
}
