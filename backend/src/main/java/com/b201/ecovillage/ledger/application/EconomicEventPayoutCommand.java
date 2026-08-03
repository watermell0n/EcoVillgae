package com.b201.ecovillage.ledger.application;

import java.util.UUID;

/**
 * 승인된 경제 이벤트가 요청하는 지급이다.
 *
 * @param approvedByUserId 승인한 교사다. 집행 주체는 SYSTEM이고 이 값은 감사 기록용이다.
 * @param amount REWARD는 학생 1인당 금액, PUBLIC_FUND는 공동기금 교부 총액이다.
 */
public record EconomicEventPayoutCommand(
    UUID classroomId,
    UUID economicEventDraftId,
    UUID approvedByUserId,
    UUID correlationId,
    long amount,
    String reason
) {
}
