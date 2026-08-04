package com.b201.ecovillage.ledger.application;

import java.util.UUID;

/**
 * 경제 이벤트 지급 결과다.
 *
 * @param replayed 같은 초안으로 이미 지급된 거래를 그대로 돌려준 경우다. 이중 지급이 아니다.
 */
public record EconomicEventPayoutResult(
    UUID transactionGroupId,
    long totalAmount,
    int recipientCount,
    boolean replayed
) {
}
