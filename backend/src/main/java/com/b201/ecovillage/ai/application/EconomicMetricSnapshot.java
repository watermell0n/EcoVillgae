package com.b201.ecovillage.ai.application;

import java.util.LinkedHashMap;
import java.util.Map;

import com.b201.ecovillage.ai.contract.BalancingContract;

public record EconomicMetricSnapshot(
    int activeStudents,
    int relatedTransactions,
    Map<BalancingContract.MetricCode, MetricValue> metrics
) {
    public EconomicMetricSnapshot {
        metrics = Map.copyOf(new LinkedHashMap<>(metrics));
    }

    public record MetricValue(
        Long value,
        BalancingContract.ValueUnit unit,
        String unavailableReason,
        String subjectLabel
    ) {
        public static MetricValue available(
            long value,
            BalancingContract.ValueUnit unit
        ) {
            return new MetricValue(value, unit, null, null);
        }

        public static MetricValue unavailable(
            BalancingContract.ValueUnit unit,
            String reason
        ) {
            return new MetricValue(null, unit, reason, null);
        }

        public boolean available() {
            return value != null;
        }
    }
}
