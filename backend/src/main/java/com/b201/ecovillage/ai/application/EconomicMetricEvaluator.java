package com.b201.ecovillage.ai.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.b201.ecovillage.ai.contract.BalancingContract;
import com.b201.ecovillage.ai.contract.BalancingContract.AnalysisSeverity;
import com.b201.ecovillage.ai.contract.BalancingContract.Direction;
import com.b201.ecovillage.ai.contract.BalancingContract.MetricCode;
import com.b201.ecovillage.ai.contract.BalancingContract.MetricState;

@Component
public class EconomicMetricEvaluator {

    public EconomicMetricEvaluation evaluate(
        Instant from,
        Instant to,
        Instant dataAsOf,
        EconomicMetricSnapshot current,
        EconomicMetricSnapshot previous,
        Set<MetricCode> requestedCodes
    ) {
        List<String> insufficient = new ArrayList<>();
        if (current.activeStudents() < 5) {
            insufficient.add("ACTIVE_STUDENTS_LT_5");
        }
        if (current.relatedTransactions() < 10) {
            insufficient.add("RELATED_TRANSACTIONS_LT_10");
        }
        BalancingContract.Sample sample = new BalancingContract.Sample(
            current.activeStudents(),
            current.relatedTransactions(),
            insufficient.isEmpty()
                ? BalancingContract.SampleStatus.SUFFICIENT
                : BalancingContract.SampleStatus.INSUFFICIENT
        );
        List<BalancingContract.Metric> metrics = requestedCodes.stream()
            .sorted(Comparator.comparing(Enum::name))
            .map(code -> metric(code, current.metrics().get(code), previous.metrics().get(code)))
            .toList();
        AnalysisSeverity severity = metrics.stream()
            .map(BalancingContract.Metric::state)
            .map(this::severity)
            .max(Comparator.comparingInt(this::rank))
            .orElse(AnalysisSeverity.NORMAL);
        long seconds = to.getEpochSecond() - from.getEpochSecond();
        Instant previousFrom = from.minusSeconds(seconds);
        return new EconomicMetricEvaluation(
            EconomicMetricEvaluation.period(from, to, dataAsOf),
            EconomicMetricEvaluation.period(previousFrom, from, dataAsOf),
            sample,
            severity,
            insufficient,
            metrics
        );
    }

    private BalancingContract.Metric metric(
        MetricCode code,
        EconomicMetricSnapshot.MetricValue current,
        EconomicMetricSnapshot.MetricValue previous
    ) {
        BalancingContract.ValueUnit unit = current == null
            ? unit(code)
            : current.unit();
        if (current == null || !current.available()) {
            return new BalancingContract.Metric(
                code,
                null,
                previous != null ? previous.value() : null,
                BalancingContract.Availability.UNAVAILABLE,
                current == null ? "METRIC_SOURCE_UNAVAILABLE" : current.unavailableReason(),
                unit,
                MetricState.UNAVAILABLE,
                Direction.NOT_APPLICABLE,
                current == null ? null : current.subjectLabel()
            );
        }
        Direction currentDirection = direction(code, current.value());
        Direction previousDirection = previous != null && previous.available()
            && supportsHistoricalComparison(code)
            ? direction(code, previous.value())
            : Direction.NOT_APPLICABLE;
        MetricState state = currentDirection == Direction.NORMAL
            || currentDirection == Direction.NOT_APPLICABLE
                ? MetricState.NORMAL
                : previousDirection == currentDirection
                    ? MetricState.WARN
                    : MetricState.OBSERVE;
        return new BalancingContract.Metric(
            code,
            current.value(),
            previous == null ? null : previous.value(),
            BalancingContract.Availability.AVAILABLE,
            null,
            unit,
            state,
            currentDirection,
            current.subjectLabel()
        );
    }

    private Direction direction(MetricCode code, long value) {
        return switch (code) {
            case LIQUID_MONEY_CHANGE_RATE_BPS -> range(value, -1500, 1500);
            case ISSUANCE_RECOVERY_RATE_BPS -> range(value, 5000, 12000);
            case ASSET_CONCENTRATION_TOP20_BPS -> high(value, 5000);
            case CONSUMPTION_RATE_BPS -> range(value, 2000, 8000);
            case PRODUCT_SELL_THROUGH_RATE_BPS -> range(value, 1000, 8000);
            case PRODUCT_STOCKOUT_SHARE_BPS -> high(value, 2000);
            case PAYMENT_MISS_RATE_BPS -> high(value, 2000);
            case REWARD_ISSUANCE_SHARE_BPS -> high(value, 3000);
            default -> Direction.NORMAL;
        };
    }

    private static Direction range(long value, long minimum, long maximum) {
        if (value < minimum) {
            return Direction.LOW;
        }
        return value > maximum ? Direction.HIGH : Direction.NORMAL;
    }

    private static Direction high(long value, long maximum) {
        return value > maximum ? Direction.HIGH : Direction.NORMAL;
    }

    private AnalysisSeverity severity(MetricState state) {
        return switch (state) {
            case WARN -> AnalysisSeverity.WARN;
            case OBSERVE -> AnalysisSeverity.OBSERVE;
            default -> AnalysisSeverity.NORMAL;
        };
    }

    private int rank(AnalysisSeverity severity) {
        return switch (severity) {
            case NORMAL -> 0;
            case OBSERVE -> 1;
            case WARN -> 2;
        };
    }

    private BalancingContract.ValueUnit unit(MetricCode code) {
        return switch (code) {
            case ISSUANCE_AMOUNT, RECOVERY_AMOUNT -> BalancingContract.ValueUnit.AMOUNT;
            default -> BalancingContract.ValueUnit.RATE_BPS;
        };
    }

    private boolean supportsHistoricalComparison(MetricCode code) {
        return code != MetricCode.PRODUCT_STOCKOUT_SHARE_BPS;
    }
}
