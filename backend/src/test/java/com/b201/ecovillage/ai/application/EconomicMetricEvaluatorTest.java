package com.b201.ecovillage.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import com.b201.ecovillage.ai.contract.BalancingContract;

class EconomicMetricEvaluatorTest {

    private final EconomicMetricEvaluator evaluator = new EconomicMetricEvaluator();

    @Test
    void raisesWarnOnlyWhenTheSameThresholdDirectionContinues() {
        BalancingContract.MetricCode code =
            BalancingContract.MetricCode.CONSUMPTION_RATE_BPS;
        EconomicMetricEvaluation result = evaluate(
            snapshot(5, 10, code, 8500L),
            snapshot(5, 10, code, 8200L),
            code
        );

        assertTrue(result.sufficient());
        assertEquals(BalancingContract.AnalysisSeverity.WARN, result.severity());
        assertEquals(BalancingContract.MetricState.WARN, result.metrics().getFirst().state());
        assertEquals(BalancingContract.Direction.HIGH, result.metrics().getFirst().direction());
    }

    @Test
    void usesObserveWhenOnlyTheCurrentPeriodBreachesTheThreshold() {
        BalancingContract.MetricCode code =
            BalancingContract.MetricCode.CONSUMPTION_RATE_BPS;
        EconomicMetricEvaluation result = evaluate(
            snapshot(5, 10, code, 8500L),
            snapshot(5, 10, code, 5000L),
            code
        );

        assertEquals(BalancingContract.AnalysisSeverity.OBSERVE, result.severity());
        assertEquals(BalancingContract.MetricState.OBSERVE, result.metrics().getFirst().state());
    }

    @Test
    void reportsBothSampleShortagesWithoutCallingThemZeroMetrics() {
        BalancingContract.MetricCode code =
            BalancingContract.MetricCode.PRODUCT_SELL_THROUGH_RATE_BPS;
        EconomicMetricSnapshot current = new EconomicMetricSnapshot(
            4,
            9,
            Map.of(code, EconomicMetricSnapshot.MetricValue.unavailable(
                BalancingContract.ValueUnit.RATE_BPS,
                "STOCK_HISTORY_UNAVAILABLE"
            ))
        );
        EconomicMetricEvaluation result = evaluate(current, current, code);

        assertFalse(result.sufficient());
        assertEquals(
            Set.of("ACTIVE_STUDENTS_LT_5", "RELATED_TRANSACTIONS_LT_10"),
            Set.copyOf(result.insufficientCodes())
        );
        assertEquals(BalancingContract.Availability.UNAVAILABLE, result.metrics().getFirst().availability());
        assertEquals(null, result.metrics().getFirst().value());
    }

    @ParameterizedTest
    @MethodSource("thresholdCases")
    void appliesEconomicThresholdV1Boundaries(
        BalancingContract.MetricCode code,
        long outside,
        long boundary,
        BalancingContract.Direction direction,
        BalancingContract.MetricState repeatedState
    ) {
        EconomicMetricEvaluation outsideResult = evaluate(
            snapshot(5, 10, code, outside),
            snapshot(5, 10, code, outside),
            code
        );
        EconomicMetricEvaluation boundaryResult = evaluate(
            snapshot(5, 10, code, boundary),
            snapshot(5, 10, code, boundary),
            code
        );

        assertEquals(direction, outsideResult.metrics().getFirst().direction());
        assertEquals(repeatedState, outsideResult.metrics().getFirst().state());
        assertEquals(BalancingContract.Direction.NORMAL, boundaryResult.metrics().getFirst().direction());
        assertEquals(BalancingContract.MetricState.NORMAL, boundaryResult.metrics().getFirst().state());
    }

    private static Stream<Arguments> thresholdCases() {
        return Stream.of(
            Arguments.of(
                BalancingContract.MetricCode.LIQUID_MONEY_CHANGE_RATE_BPS,
                -1501L,
                -1500L,
                BalancingContract.Direction.LOW,
                BalancingContract.MetricState.WARN
            ),
            Arguments.of(
                BalancingContract.MetricCode.ISSUANCE_RECOVERY_RATE_BPS,
                4999L,
                5000L,
                BalancingContract.Direction.LOW,
                BalancingContract.MetricState.WARN
            ),
            Arguments.of(
                BalancingContract.MetricCode.ASSET_CONCENTRATION_TOP20_BPS,
                5001L,
                5000L,
                BalancingContract.Direction.HIGH,
                BalancingContract.MetricState.WARN
            ),
            Arguments.of(
                BalancingContract.MetricCode.CONSUMPTION_RATE_BPS,
                1999L,
                2000L,
                BalancingContract.Direction.LOW,
                BalancingContract.MetricState.WARN
            ),
            Arguments.of(
                BalancingContract.MetricCode.PRODUCT_SELL_THROUGH_RATE_BPS,
                999L,
                1000L,
                BalancingContract.Direction.LOW,
                BalancingContract.MetricState.WARN
            ),
            Arguments.of(
                BalancingContract.MetricCode.PRODUCT_STOCKOUT_SHARE_BPS,
                2001L,
                2000L,
                BalancingContract.Direction.HIGH,
                BalancingContract.MetricState.OBSERVE
            ),
            Arguments.of(
                BalancingContract.MetricCode.PAYMENT_MISS_RATE_BPS,
                2001L,
                2000L,
                BalancingContract.Direction.HIGH,
                BalancingContract.MetricState.WARN
            ),
            Arguments.of(
                BalancingContract.MetricCode.REWARD_ISSUANCE_SHARE_BPS,
                3001L,
                3000L,
                BalancingContract.Direction.HIGH,
                BalancingContract.MetricState.WARN
            )
        );
    }

    private EconomicMetricEvaluation evaluate(
        EconomicMetricSnapshot current,
        EconomicMetricSnapshot previous,
        BalancingContract.MetricCode code
    ) {
        return evaluator.evaluate(
            Instant.parse("2026-07-25T00:00:00Z"),
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-01T01:00:00Z"),
            current,
            previous,
            Set.of(code)
        );
    }

    private EconomicMetricSnapshot snapshot(
        int students,
        int transactions,
        BalancingContract.MetricCode code,
        long value
    ) {
        return new EconomicMetricSnapshot(
            students,
            transactions,
            Map.of(code, EconomicMetricSnapshot.MetricValue.available(
                value,
                BalancingContract.ValueUnit.RATE_BPS
            ))
        );
    }
}
