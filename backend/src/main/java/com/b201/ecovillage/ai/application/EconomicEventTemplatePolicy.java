package com.b201.ecovillage.ai.application;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.b201.ecovillage.ai.contract.EconomicEventContract.InterestRateScope;
import com.b201.ecovillage.ai.contract.EconomicEventContract.TemplateType;

/**
 * 경제 이벤트 템플릿의 학년 정책과 효과 범위를 결정한다.
 * 효과와 금지 사항은 Spring이 정하고 AI는 이야기 문구만 만든다.
 */
@Component
public class EconomicEventTemplatePolicy {

    private static final int MIN_GRADE = 4;
    private static final int MAX_GRADE = 6;

    /**
     * docs/ai/ECONOMIC_EVENT.md 2장의 템플릿별 허용 학년이다.
     */
    private static final java.util.Map<TemplateType, Integer> MINIMUM_GRADE = java.util.Map.of(
        TemplateType.PRICE, 4,
        TemplateType.SUPPLY, 4,
        TemplateType.PUBLIC_FUND, 4,
        TemplateType.TAX, 5,
        TemplateType.INTEREST_RATE, 5,
        TemplateType.REWARD, 6,
        TemplateType.INVESTMENT_NEWS, 5
    );

    /**
     * 모든 템플릿에 공통으로 금지하는 효과다. AI 출력 검증에도 그대로 쓰인다.
     */
    private static final List<String> COMMON_PROHIBITED = List.of(
        "특정 학생을 지목한 손익",
        "강제 대출과 강제 상환",
        "개인 잔액과 계좌의 직접 변경",
        "음수 잔액 발생",
        "교사 승인 없는 실행과 공개",
        "실제 금융 조언과 실제 기업 언급",
        "미래 가격과 수익률 예측"
    );

    public boolean supportedGrade(TemplateType template, int grade) {
        if (grade < MIN_GRADE || grade > MAX_GRADE) {
            return false;
        }
        return grade >= MINIMUM_GRADE.get(template);
    }

    /**
     * 초5는 예금·적금 금리만 다루고 대출 금리는 초6부터 허용한다.
     */
    public boolean supportedInterestRateScope(int grade, InterestRateScope scope) {
        if (scope == InterestRateScope.LOAN) {
            return grade >= 6;
        }
        return grade >= 5;
    }

    public Set<String> requiredModules(TemplateType template) {
        return switch (template) {
            case PRICE, SUPPLY -> Set.of("STORE");
            case TAX -> Set.of("INCOME_TAX");
            case INTEREST_RATE -> Set.of("DEPOSIT");
            case REWARD -> Set.of("QUIZ_GAME");
            case PUBLIC_FUND -> Set.of("PUBLIC_FUND");
            case INVESTMENT_NEWS -> Set.of("INVESTMENT");
        };
    }

    /**
     * 규칙 엔진이 정하는 효과 범위다. 수치는 승인 단계에서 템플릿 계산기가 확정하며
     * 초안 단계에서는 무엇이 바뀌는지만 문장으로 고정한다.
     */
    public List<String> effectSummary(TemplateType template, InterestRateScope scope) {
        return switch (template) {
            case PRICE -> List.of(
                "공식 상점 상품군의 판매 가격이 조정됩니다.",
                "학생 보유 금액은 그대로이고 구매력만 달라집니다."
            );
            case SUPPLY -> List.of(
                "일부 상품의 재고와 재입고 속도가 달라집니다.",
                "품절 상품은 교사가 재고를 채울 때까지 구매할 수 없습니다."
            );
            case TAX -> List.of(
                "다음 급여부터 적용되는 소득세율이 조정됩니다.",
                "이미 지급된 급여와 기존 거래에는 소급하지 않습니다.",
                "회수된 세금은 공동기금으로 이동합니다."
            );
            case INTEREST_RATE -> List.of(
                scope == InterestRateScope.LOAN
                    ? "신규 대출 상품의 금리가 조정됩니다."
                    : "신규 예금·적금 상품의 금리가 조정됩니다.",
                "이미 가입한 상품은 가입 당시 금리를 유지합니다."
            );
            case REWARD -> List.of(
                "학급 전체 활동 보상 금액이 조정됩니다.",
                "보상은 활동을 마친 학생에게 동일하게 지급됩니다.",
                "보상 발행액은 전체 발행액 비중 검사에 포함됩니다."
            );
            case PUBLIC_FUND -> List.of(
                "공동기금 사용처를 학급이 함께 정합니다.",
                "실제 지출은 교사 승인과 투표 결과가 있어야 발생합니다."
            );
            case INVESTMENT_NEWS -> List.of(
                "현재 공개된 가격 단계에 연결된 뉴스만 제공합니다."
            );
        };
    }

    public List<String> prohibitedEffects(TemplateType template) {
        if (template == TemplateType.REWARD) {
            return concat(COMMON_PROHIBITED, "TEST 퀴즈에 화폐 보상 지급");
        }
        if (template == TemplateType.INTEREST_RATE) {
            return concat(COMMON_PROHIBITED, "기존 예금·대출 금리의 소급 변경");
        }
        return COMMON_PROHIBITED;
    }

    private static List<String> concat(List<String> base, String extra) {
        return java.util.stream.Stream.concat(base.stream(), java.util.stream.Stream.of(extra))
            .toList();
    }
}
