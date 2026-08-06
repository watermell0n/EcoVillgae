package com.b201.ecovillage.ruleengine.domain;

public enum RuleTargetType {
    ROLE_SALARY,
    INCOME_TAX_RATE,
    DEPOSIT_RATE,
    SAVINGS_RATE,
    LOAN_RATE,
    PRODUCT_PRICE,
    GAME_REWARD,
    /**
     * 투자 운영 조건이다. 하루 변동폭과 종목별 1주 시작가를 함께 추천하므로 후보가 여러 개
     * 나오지만, 모두 투자 모듈 하나를 가리키도록 같은 대상 유형을 쓴다.
     */
    INVESTMENT_CONDITION
}
