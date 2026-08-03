package com.b201.ecovillage.ai.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BalancingContract {

    public static final String SCHEMA_VERSION = "1.2";
    public static final String PROMPT_VERSION = "balancing-v1";
    public static final String METRIC_VERSION = "economic-metrics-v1";
    public static final String THRESHOLD_VERSION = "economic-thresholds-v1";

    private BalancingContract() {
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
        AnalysisMode analysisMode,
        TriggerType triggerType,
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
                analysisMode,
                triggerType,
                payload
            );
        }
    }

    public record Payload(
        String metricVersion,
        String thresholdVersion,
        Period period,
        Period previousPeriod,
        AnalysisSeverity analysisSeverity,
        Sample sample,
        List<Metric> metrics,
        List<AllowedAction> allowedActions
    ) {
        public Payload {
            metrics = List.copyOf(metrics);
            allowedActions = List.copyOf(allowedActions);
        }
    }

    public record Period(Instant from, Instant to, Instant dataAsOf) {
    }

    public record Sample(
        int activeStudents,
        int relatedTransactions,
        SampleStatus status
    ) {
    }

    public record Metric(
        MetricCode code,
        Long value,
        Long previousValue,
        Availability availability,
        String unavailableReason,
        ValueUnit unit,
        MetricState state,
        Direction direction,
        String subjectLabel
    ) {
    }

    public record AllowedAction(
        String actionId,
        TargetType targetType,
        UUID targetId,
        long currentValue,
        long minValue,
        long maxValue,
        ValueUnit unit,
        String label
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
        AnalysisMode analysisMode,
        TriggerType triggerType,
        AnalysisSeverity analysisSeverity,
        String metricVersion,
        String thresholdVersion,
        Period period,
        Period previousPeriod,
        String summary,
        List<MetricCode> evidenceMetricCodes,
        List<Cause> causes,
        List<Proposal> proposals
    ) {
        public Output {
            evidenceMetricCodes = List.copyOf(evidenceMetricCodes);
            causes = List.copyOf(causes);
            proposals = List.copyOf(proposals);
        }
    }

    public record Cause(String text, List<MetricCode> evidenceMetricCodes) {
        public Cause {
            evidenceMetricCodes = List.copyOf(evidenceMetricCodes);
        }
    }

    public record Proposal(
        String actionId,
        TargetType targetType,
        String label,
        long currentValue,
        long minValue,
        long maxValue,
        ValueUnit unit,
        long proposedValue,
        List<MetricCode> evidenceMetricCodes,
        String rationale,
        String expectedEffect,
        List<String> sideEffects,
        int priority
    ) {
        public Proposal {
            evidenceMetricCodes = List.copyOf(evidenceMetricCodes);
            sideEffects = List.copyOf(sideEffects);
        }
    }

    public enum AnalysisMode {
        ON_DEMAND,
        AUTO_MONITOR
    }

    public enum TriggerType {
        TEACHER_REQUEST,
        AUTO_MONITOR
    }

    public enum AnalysisSeverity {
        NORMAL,
        OBSERVE,
        WARN
    }

    public enum SampleStatus {
        SUFFICIENT,
        INSUFFICIENT
    }

    public enum Availability {
        AVAILABLE,
        UNAVAILABLE
    }

    public enum MetricState {
        NORMAL,
        OBSERVE,
        WARN,
        UNAVAILABLE
    }

    public enum Direction {
        LOW,
        NORMAL,
        HIGH,
        NOT_APPLICABLE
    }

    public enum ValueUnit {
        AMOUNT,
        COUNT,
        RATE_BPS
    }

    public enum MetricCode {
        LIQUID_MONEY_CHANGE_RATE_BPS,
        ISSUANCE_AMOUNT,
        RECOVERY_AMOUNT,
        ISSUANCE_RECOVERY_RATE_BPS,
        WALLET_AVERAGE_CHANGE_RATE_BPS,
        WALLET_MEDIAN_CHANGE_RATE_BPS,
        ASSET_CONCENTRATION_TOP20_BPS,
        CONSUMPTION_RATE_BPS,
        PRODUCT_SELL_THROUGH_RATE_BPS,
        PRODUCT_STOCKOUT_SHARE_BPS,
        FINANCIAL_PARTICIPATION_RATE_BPS,
        PAYMENT_MISS_RATE_BPS,
        INVESTMENT_PARTICIPATION_RATE_BPS,
        INVESTMENT_MEDIAN_RETURN_BPS,
        INVESTMENT_LOSS_PARTICIPANT_RATE_BPS,
        REWARD_ISSUANCE_SHARE_BPS
    }

    public enum TargetType {
        PRODUCT_PRICE,
        PRODUCT_STOCK,
        ACTIVITY_REWARD,
        GAME_REWARD,
        QUIZ_REWARD,
        TAX_RATE,
        INFLATION_RATE,
        DEPOSIT_RATE,
        SAVINGS_RATE,
        LOAN_RATE
    }
}
