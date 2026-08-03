package com.b201.ecovillage.ai.contract;

import java.time.Instant;
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
        String teacherContext
    ) {
        public Payload {
            sourceEvidenceCodes = List.copyOf(sourceEvidenceCodes);
            effectSummary = List.copyOf(effectSummary);
            prohibitedEffects = List.copyOf(prohibitedEffects);
        }
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
        List<String> progressMessages
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
}
