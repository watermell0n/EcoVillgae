package com.b201.ecovillage.ruleengine.domain;

/**
 * 규칙 엔진이 값을 정할 때 참고하는 학급의 지금 상태다.
 *
 * <p>학급을 막 만든 시점에는 거래가 없어 대부분 0이고, 그때는 규칙 엔진이 급여 계획으로 계산한
 * 예정 발행액을 대신 쓴다. {@code null}은 "아직 알 수 없음"이고 0은 "정말로 없음"이다.
 *
 * @param moneyIssuance             지금까지 발행한 화폐 총액
 * @param realizedIssuance          학생 지갑으로 들어온 발행액 합계(급여·보상·대출 실행)
 * @param realizedRecovery          학생 지갑에서 빠져나간 회수액 합계(소비·세금·상환)
 * @param realizedSalaryIssuance    그 가운데 급여로 나간 금액
 * @param averageStudentBalance     학생 지갑 잔액 평균
 * @param medianStudentBalance      학생 지갑 잔액 중앙값
 * @param storeAveragePrice         게시된 유료 상품의 평균 단가(0이면 상점이 비어 있음)
 * @param storeInventoryValue       게시된 유료 상품의 재고 총액
 * @param paymentMissBps            적금·대출 납입 연체율(bps). null이면 아직 판단할 수 없음
 * @param financialParticipationBps 예금·적금 이용 학생 비율(bps). null이면 알 수 없음
 * @param walletCount               잔액을 읽어 온 학생 지갑 수
 */
public record ClassroomEconomicMetrics(
    long moneyIssuance,
    long realizedIssuance,
    long realizedRecovery,
    long realizedSalaryIssuance,
    long averageStudentBalance,
    long medianStudentBalance,
    long storeAveragePrice,
    long storeInventoryValue,
    Integer paymentMissBps,
    Integer financialParticipationBps,
    int walletCount
) {

    /** 아직 아무 지표도 읽지 못한 상태다. 규칙 엔진은 이때 계획값만으로 계산한다. */
    public static ClassroomEconomicMetrics empty() {
        return new ClassroomEconomicMetrics(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, null, null, 0);
    }

    /** 운영이 시작돼 발행이 실제로 일어났는지다. 실측값을 믿어도 되는지 판단하는 기준이다. */
    public boolean hasLedgerHistory() {
        return realizedIssuance > 0L || moneyIssuance > 0L;
    }

    public boolean hasStorePrices() {
        return storeAveragePrice > 0L;
    }
}
