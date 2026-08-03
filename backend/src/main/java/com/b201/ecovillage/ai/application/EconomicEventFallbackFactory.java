package com.b201.ecovillage.ai.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.b201.ecovillage.ai.contract.EconomicEventContract;

/**
 * AI 설명 연결이 끊겼을 때 규칙 엔진이 정한 효과만으로 검토용 초안을 만든다.
 * 이야기 문구가 없을 뿐 교사가 검토·수정할 수 있는 형태는 유지한다.
 */
@Component
public class EconomicEventFallbackFactory {

    public EconomicEventContract.Response create(
        EconomicEventContract.Request request,
        String failureCode
    ) {
        EconomicEventContract.Payload payload = request.payload();
        List<String> validationCodes = new ArrayList<>();
        validationCodes.add("SPRING_EVENT_FALLBACK_USED");
        validationCodes.add(failureCode);
        List<EconomicEventContract.Choice> choices = List.of(
            new EconomicEventContract.Choice(
                "APPLY",
                "규칙 엔진이 정한 효과로 진행",
                String.join(" ", payload.effectSummary())
            ),
            new EconomicEventContract.Choice(
                "SKIP",
                "이번에는 진행하지 않음",
                "학급 경제 설정을 그대로 둡니다."
            )
        );
        return new EconomicEventContract.Response(
            request.aiRunId(),
            EconomicEventContract.SCHEMA_VERSION,
            EconomicEventContract.PROMPT_VERSION,
            "SPRING_FALLBACK",
            "deterministic-event-fallback",
            "PASSED",
            validationCodes,
            new EconomicEventContract.Usage(0, 0),
            new EconomicEventContract.Output(
                "REVIEW_REQUIRED",
                payload.templateType(),
                payload.sourceType(),
                payload.sourceProposalId(),
                payload.sourceEvidenceCodes(),
                payload.interestRateScope(),
                title(payload.templateType()),
                "AI 이야기 생성에 실패해 규칙 엔진이 정한 효과만 표시합니다. "
                    + "교사가 내용을 직접 작성하거나 다시 생성해 주세요.",
                choices,
                List.of("교사 검토가 필요한 초안입니다."),
                // 대체안도 입력받은 단계 연결을 그대로 유지한다.
                binding(payload.investmentNewsContext())
            )
        );
    }

    private static EconomicEventContract.InvestmentNewsBinding binding(
        EconomicEventContract.InvestmentNewsContext context
    ) {
        if (context == null) {
            return null;
        }
        return new EconomicEventContract.InvestmentNewsBinding(
            context.scenarioVersionId(),
            context.assetId(),
            context.priceStepId(),
            context.targetPriceStepId(),
            context.assetName(),
            context.assetSymbol(),
            context.investmentMode(),
            context.scenarioSource(),
            context.settlementRule(),
            context.currentStepIndex(),
            context.targetStepIndex()
        );
    }

    private static String title(EconomicEventContract.TemplateType template) {
        return switch (template) {
            case PRICE -> "물가 변동 안내";
            case SUPPLY -> "상품 공급 변화 안내";
            case TAX -> "세금 조정 안내";
            case INTEREST_RATE -> "금리 조정 안내";
            case REWARD -> "활동 보상 조정 안내";
            case PUBLIC_FUND -> "공동기금 사용 논의";
            case INVESTMENT_NEWS -> "투자 뉴스 안내";
        };
    }
}
