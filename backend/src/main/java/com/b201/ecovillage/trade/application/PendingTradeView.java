package com.b201.ecovillage.trade.application;

import java.time.Instant;
import java.util.UUID;

/**
 * 교사 승인을 기다리는 거래 한 건이다.
 *
 * <p>승인·정산 화면은 "누가 누구에게 얼마를" 만 보여 주면 되므로, 물건 목록까지 담지 않고
 * 오가는 금액 합계와 양쪽 학생 이름만 담는다.
 */
public record PendingTradeView(
    UUID tradeId,
    UUID proposerStudentId,
    String proposerNickname,
    UUID counterpartStudentId,
    String counterpartNickname,
    String description,
    long amount,
    int itemCount,
    String status,
    long stateVersion,
    Instant createdAt
) {
}
