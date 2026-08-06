package com.b201.ecovillage.finance.domain;

public final class LoanCreditPolicy {

    private LoanCreditPolicy() {
    }

    public static long availableLimit(long maximumPrincipal, int score) {
        if (maximumPrincipal < 1 || score < 0 || score > 1_000) {
            throw new IllegalArgumentException("loan credit values are invalid");
        }
        if (score >= 750) return maximumPrincipal;
        if (score >= 600) return maximumPrincipal * 80 / 100;
        if (score >= 450) return maximumPrincipal * 60 / 100;
        return 0;
    }

    public static String level(int score) {
        if (score >= 750) return "GOOD";
        if (score >= 600) return "NORMAL";
        if (score >= 450) return "CAUTION";
        return "RESTRICTED";
    }

    /** 우량 등급은 1%p 할인하고 주의 등급은 2%p 가산한다. */
    public static int interestRateForLevel(int baseInterestRateBps, String level) {
        if (baseInterestRateBps < 0 || baseInterestRateBps > 2_000 || level == null) {
            throw new IllegalArgumentException("loan credit rate values are invalid");
        }
        int adjusted = switch (level) {
            case "GOOD" -> baseInterestRateBps - 100;
            case "NORMAL" -> baseInterestRateBps;
            case "CAUTION" -> baseInterestRateBps + 200;
            case "RESTRICTED" -> baseInterestRateBps + 400;
            default -> throw new IllegalArgumentException("unsupported credit level");
        };
        return Math.max(0, Math.min(2_000, adjusted));
    }
}
