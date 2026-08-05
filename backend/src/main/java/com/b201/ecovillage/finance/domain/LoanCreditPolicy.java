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
}
