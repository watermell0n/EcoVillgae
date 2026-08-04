package com.b201.ecovillage.ai.contract;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class EconomicEventContract {

    public static final String SCHEMA_VERSION = "1.2";
    public static final String PROMPT_VERSION = "economic-event-v1";
    public static final String LOCALE = "ko-KR";

    private EconomicEventContract() {
    }

    public record Request(
        UUID aiRunId,
        UUID requestId,
        UUID correlationId,
        String schemaVersion,
        String promptVersion,
        int attempt,
        Instant deadlineAt,
        String locale,
        int grade,
        Payload payload
    ) {
        public Request withAttempt(int value) {
            return new Request(
                aiRunId,
                requestId,
                correlationId,
                schemaVersion,
                promptVersion,
                value,
                deadlineAt,
                locale,
                grade,
                payload
            );
        }
    }

    public record Payload(
        CourseScale courseScale,
        SourceType sourceType,
        UUID sourceProposalId,
        List<String> sourceEvidenceCodes,
        TemplateType templateType,
        InterestRateScope interestRateScope,
        List<String> effectSummary,
        List<String> prohibitedEffects,
        String teacherContext,
        /** INVESTMENT_NEWS 전용이다. 나머지 템플릿에서는 null이다. */
        InvestmentNewsContext investmentNewsContext
    ) {
        public Payload {
            sourceEvidenceCodes = List.copyOf(sourceEvidenceCodes);
            effectSummary = List.copyOf(effectSummary);
            prohibitedEffects = List.copyOf(prohibitedEffects);
        }
    }

    /**
     * 투자 뉴스가 쓸 공개 시세다. 가격은 시나리오에 이미 정해져 있고 AI는 설명만 만든다.
     *
     * <p>{@code publicPriceFacts}는 1단계부터 {@code currentStepIndex}까지 빠짐없이 오름차순이어야
     * 한다. 뉴스가 학생이 이미 본 시세와 어긋나지 않게 하려면 현재 단계만으로는 부족하다.
     */
    public record InvestmentNewsContext(
        UUID scenarioVersionId,
        UUID assetId,
        UUID priceStepId,
        UUID targetPriceStepId,
        String assetName,
        String assetSymbol,
        String fictionalCompanyProfile,
        InvestmentMode investmentMode,
        InvestmentScenarioSource scenarioSource,
        InvestmentSettlementRule settlementRule,
        int currentStepIndex,
        int targetStepIndex,
        InvestmentTargetDirection targetDirection,
        HistoricalIndexReference historicalIndexReference,
        List<InvestmentPriceFact> publicPriceFacts
    ) {
        public InvestmentNewsContext {
            publicPriceFacts = List.copyOf(publicPriceFacts);
        }
    }

    public record InvestmentPriceFact(
        int stepIndex,
        long price,
        int changeRateBps,
        java.time.Instant revealedAt
    ) {
    }

    public record HistoricalIndexReference(
        String seriesCode,
        String eventName,
        String sourceName,
        String sourceUrl,
        LocalDate periodStart,
        LocalDate periodEnd
    ) {
    }

    /**
     * AI가 출력에서 되돌려주는 단계 연결이다. 입력과 다르면 출력을 거절한다.
     */
    public record InvestmentNewsBinding(
        UUID scenarioVersionId,
        UUID assetId,
        UUID priceStepId,
        UUID targetPriceStepId,
        String assetName,
        String assetSymbol,
        InvestmentMode investmentMode,
        InvestmentScenarioSource scenarioSource,
        InvestmentSettlementRule settlementRule,
        int currentStepIndex,
        int targetStepIndex
    ) {
    }

    public record Response(
        UUID aiRunId,
        String schemaVersion,
        String promptVersion,
        String provider,
        String modelId,
        String safetyStatus,
        List<String> validationCodes,
        Usage usage,
        Output output
    ) {
        public Response {
            validationCodes = List.copyOf(validationCodes);
        }
    }

    public record Usage(int inputTokens, int outputTokens) {
    }

    public record Output(
        String reviewStatus,
        TemplateType templateType,
        SourceType sourceType,
        UUID sourceProposalId,
        List<String> sourceEvidenceCodes,
        InterestRateScope interestRateScope,
        String title,
        String narrative,
        List<Choice> choices,
        List<String> progressMessages,
        /** INVESTMENT_NEWS 전용이다. 나머지 템플릿에서는 null이어야 한다. */
        InvestmentNewsBinding investmentNewsBinding,
        /** INVESTMENT_NEWS 전용 교사용 설명이다. 나머지 템플릿에서는 null이어야 한다. */
        String teacherExplanation
    ) {
        public Output {
            sourceEvidenceCodes = List.copyOf(sourceEvidenceCodes);
            choices = List.copyOf(choices);
            progressMessages = List.copyOf(progressMessages);
        }
    }

    public record Choice(String choiceKey, String label, String consequenceText) {
    }

    public enum CourseScale {
        ONE_SHOT,
        ONE_WEEK,
        ONE_MONTH,
        ONE_SEMESTER
    }

    public enum SourceType {
        TEACHER_REQUEST,
        MONITOR_RECOMMENDATION
    }

    public enum TemplateType {
        PRICE,
        SUPPLY,
        TAX,
        INTEREST_RATE,
        INVESTMENT_NEWS,
        REWARD,
        PUBLIC_FUND
    }

    public enum InterestRateScope {
        DEPOSIT,
        SAVINGS,
        LOAN
    }

    public enum InvestmentMode {
        SHORT_TERM,
        LONG_TERM
    }

    public enum InvestmentScenarioSource {
        AI_GENERATED,
        HISTORICAL_INDEX
    }

    public enum InvestmentSettlementRule {
        NEXT_DAY_MANDATORY,
        COURSE_END_FINAL
    }

    public enum InvestmentTargetDirection {
        RISE,
        FALL,
        FLAT
    }
}
