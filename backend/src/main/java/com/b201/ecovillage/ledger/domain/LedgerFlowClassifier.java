package com.b201.ecovillage.ledger.domain;

import java.util.Locale;

/**
 * 학생 지갑의 돈 흐름을 발행·회수·이동으로 나눈다.
 *
 * <p>운영 지표 집계와 규칙 엔진 입력이 같은 기준을 써야 "발행 대비 회수"가 화면마다 달라지지
 * 않는다. 그래서 분류 기준을 이 한 곳에만 둔다.
 */
public final class LedgerFlowClassifier {

    private LedgerFlowClassifier() {
    }

    public enum Flow {
        /** 학급 밖에서 새로 만들어져 지갑으로 들어온 돈이다. */
        ISSUANCE,
        /** 지갑에서 빠져나가 학급 밖으로 사라진 돈이다. */
        RECOVERY,
        /** 학생끼리 주고받거나 계좌 사이를 옮긴 돈이라 통화량은 그대로다. */
        RECLASSIFICATION
    }

    public static Flow classify(String transactionType, long walletDelta) {
        String type = normalized(transactionType);
        if (
            type.contains("PERSONAL_TRADE") || type.contains("TRANSFER")
                || type.contains("DEPOSIT_OPEN") || type.contains("SAVINGS_OPEN")
                || type.contains("INVESTMENT_BUY") || type.contains("INVESTMENT_SELL")
        ) {
            return Flow.RECLASSIFICATION;
        }
        if (
            type.contains("SALARY") || type.contains("REWARD")
                || type.contains("LOAN_DISBURSE") || type.contains("LOAN_EXECUTION")
        ) {
            return Flow.ISSUANCE;
        }
        if (
            type.contains("STORE_PURCHASE") || type.contains("TAX")
                || type.contains("LOAN_REPAY") || type.contains("FEE")
        ) {
            return Flow.RECOVERY;
        }
        if (walletDelta > 0) {
            return Flow.ISSUANCE;
        }
        return walletDelta < 0 ? Flow.RECOVERY : Flow.RECLASSIFICATION;
    }

    public static boolean isReward(String transactionType) {
        return normalized(transactionType).contains("REWARD");
    }

    public static boolean isSalary(String transactionType) {
        return normalized(transactionType).contains("SALARY");
    }

    public static boolean isConsumption(String transactionType) {
        String type = normalized(transactionType);
        return type.contains("STORE_PURCHASE") || type.contains("CONSUMPTION");
    }

    public static boolean isTax(String transactionType) {
        return normalized(transactionType).contains("TAX");
    }

    public static String normalized(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }
}
