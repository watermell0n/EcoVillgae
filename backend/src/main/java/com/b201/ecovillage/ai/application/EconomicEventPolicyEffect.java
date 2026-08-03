package com.b201.ecovillage.ai.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.b201.ecovillage.ai.contract.EconomicEventContract.InterestRateScope;
import com.b201.ecovillage.ai.contract.EconomicEventContract.TemplateType;

/**
 * 승인된 경제 이벤트가 무엇을 바꿀 수 있는지 템플릿별로 고정한다.
 *
 * <p>docs/AI_DIRECTION.md 5장에 따라 효과 수치는 AI가 아니라 규칙과 교사가 정한다.
 * 이 클래스는 "어떤 항목을 건드릴 수 있는지"만 정하고 값의 크기는 판단하지 않는다.
 *
 * <p>효과는 두 갈래다. TAX·금리·가격·재고는 앞으로의 거래에만 적용되므로 정책 버전만 올리고
 * 원장 거래를 만들지 않는다. REWARD와 PUBLIC_FUND는 실제로 화폐가 움직이므로 발행 계좌에서
 * 나가는 원장 거래를 만든다.
 */
@Component
public class EconomicEventPolicyEffect {

    static final String PRICE_ADJUSTMENT_BPS = "price_adjustment_bps";
    static final String INFLATION_RATE_BPS = "inflation_rate_bps";
    static final String INCOME_TAX_RATE_BPS = "income_tax_rate_bps";
    static final String DEPOSIT_RATE_BPS = "deposit_rate_bps";
    static final String LOAN_RATE_BPS = "loan_rate_bps";
    static final String STOCK_POLICY = "stock_policy";

    /** REWARD가 받는 학생 1인당 지급액이다. */
    static final String REWARD_PER_STUDENT = "reward_per_student";
    /** PUBLIC_FUND가 받는 공동기금 교부 총액이다. */
    static final String PUBLIC_FUND_GRANT_AMOUNT = "public_fund_grant_amount";

    /**
     * 승인 시 어느 도메인 진입점을 호출할지 결정한다.
     */
    public enum EffectKind {
        /** 경제 정책 버전만 올린다. 원장 거래는 없다. */
        POLICY,
        /** 발행 계좌에서 학생 전원에게 지급한다. */
        REWARD_ISSUANCE,
        /** 발행 계좌에서 학급 공동기금으로 교부한다. */
        PUBLIC_FUND_GRANT,
        /**
         * 연결된 가격 단계의 공개 대기 상태로만 바꾼다. 정책도 원장도 건드리지 않는다.
         * 뉴스는 이야기일 뿐이고 가격은 시나리오에 이미 정해져 있다.
         */
        INVESTMENT_NEWS_RELEASE
    }

    public EffectKind kind(TemplateType template) {
        return switch (template) {
            case PRICE, SUPPLY, TAX, INTEREST_RATE -> EffectKind.POLICY;
            case REWARD -> EffectKind.REWARD_ISSUANCE;
            case PUBLIC_FUND -> EffectKind.PUBLIC_FUND_GRANT;
            case INVESTMENT_NEWS -> EffectKind.INVESTMENT_NEWS_RELEASE;
        };
    }

    /**
     * 템플릿이 바꿀 수 있는 항목이다. 원장 갈래는 금액 항목 하나만 받는다.
     */
    public Set<String> editableFields(TemplateType template, InterestRateScope scope) {
        return switch (template) {
            case PRICE -> ordered(PRICE_ADJUSTMENT_BPS, INFLATION_RATE_BPS);
            case SUPPLY -> ordered(STOCK_POLICY);
            case TAX -> ordered(INCOME_TAX_RATE_BPS);
            case INTEREST_RATE -> scope == InterestRateScope.LOAN
                ? ordered(LOAN_RATE_BPS)
                : ordered(DEPOSIT_RATE_BPS);
            case REWARD -> ordered(REWARD_PER_STUDENT);
            case PUBLIC_FUND -> ordered(PUBLIC_FUND_GRANT_AMOUNT);
            case INVESTMENT_NEWS -> Set.of();
        };
    }

    /**
     * 원장 갈래가 금액을 읽어올 항목 이름이다.
     */
    public String amountField(TemplateType template) {
        return switch (template) {
            case REWARD -> REWARD_PER_STUDENT;
            case PUBLIC_FUND -> PUBLIC_FUND_GRANT_AMOUNT;
            default -> throw new IllegalArgumentException(
                "Template " + template + " does not move money."
            );
        };
    }

    private static Set<String> ordered(String... fields) {
        return java.util.Collections.unmodifiableSet(
            new LinkedHashSet<>(List.of(fields))
        );
    }
}
