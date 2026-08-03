package com.b201.ecovillage.ai.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.b201.ecovillage.ai.contract.EconomicEventContract.InterestRateScope;
import com.b201.ecovillage.ai.contract.EconomicEventContract.TemplateType;

/**
 * 승인된 경제 이벤트가 바꿀 수 있는 경제 정책 항목을 템플릿별로 고정한다.
 *
 * <p>docs/AI_DIRECTION.md 5장에 따라 효과 수치는 AI가 아니라 규칙과 교사가 정한다.
 * 이 클래스는 "어떤 항목을 건드릴 수 있는지"만 정하고 값의 크기는 판단하지 않는다.
 * 값의 허용 범위 검사는 ECONOMIC 정책의 소유자인
 * {@code EconomicPolicyCommandService}가 그대로 담당한다.
 *
 * <p>7종 템플릿의 효과는 모두 앞으로의 거래에만 적용된다. 개인 잔액을 직접 바꾸지 않으므로
 * 승인 경로는 거래 원장에 아무것도 기록하지 않는다. API-AI-006의 x-storage-targets에
 * 원장 테이블이 없는 것과 같은 이유다.
 */
@Component
public class EconomicEventPolicyEffect {

    static final String PRICE_ADJUSTMENT_BPS = "price_adjustment_bps";
    static final String INFLATION_RATE_BPS = "inflation_rate_bps";
    static final String INCOME_TAX_RATE_BPS = "income_tax_rate_bps";
    static final String DEPOSIT_RATE_BPS = "deposit_rate_bps";
    static final String LOAN_RATE_BPS = "loan_rate_bps";
    static final String STOCK_POLICY = "stock_policy";
    static final String REWARD_POLICY = "reward_policy";

    /**
     * 템플릿이 바꿀 수 있는 정책 항목이다. 빈 집합이면 승인만으로 게시하고 정책은 그대로 둔다.
     *
     * <p>PUBLIC_FUND는 사용처를 학급이 함께 정하는 안내이고 실제 지출은 투표와 별도 승인에서
     * 발생하므로 이 경로에서 정책을 바꾸지 않는다. INVESTMENT_NEWS는 가격 단계에 뉴스를
     * 연결할 뿐 경제 정책을 바꾸지 않는다.
     */
    public Set<String> editableFields(TemplateType template, InterestRateScope scope) {
        return switch (template) {
            case PRICE -> ordered(PRICE_ADJUSTMENT_BPS, INFLATION_RATE_BPS);
            case SUPPLY -> ordered(STOCK_POLICY);
            case TAX -> ordered(INCOME_TAX_RATE_BPS);
            case INTEREST_RATE -> scope == InterestRateScope.LOAN
                ? ordered(LOAN_RATE_BPS)
                : ordered(DEPOSIT_RATE_BPS);
            case REWARD -> ordered(REWARD_POLICY);
            case PUBLIC_FUND, INVESTMENT_NEWS -> Set.of();
        };
    }

    /**
     * 값이 정수여야 하는 항목이다. 나머지는 정수 트리를 담은 맵으로 받는다.
     */
    public boolean isScalarField(String field) {
        return !STOCK_POLICY.equals(field) && !REWARD_POLICY.equals(field);
    }

    private static Set<String> ordered(String... fields) {
        return java.util.Collections.unmodifiableSet(
            new LinkedHashSet<>(List.of(fields))
        );
    }
}
