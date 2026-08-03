package com.b201.ecovillage.investment.domain;

public enum InvestmentMode {
    SHORT_TERM,
    LONG_TERM;

    public String settlementRule() {
        return this == SHORT_TERM ? "NEXT_DAY_MANDATORY" : "COURSE_END_FINAL";
    }
}
