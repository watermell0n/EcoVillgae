package com.b201.ecovillage.ai.application;

import java.time.Instant;
import java.util.List;

import com.b201.ecovillage.ai.contract.BalancingContract;

public record EconomicMetricEvaluation(
    BalancingContract.Period period,
    BalancingContract.Period previousPeriod,
    BalancingContract.Sample sample,
    BalancingContract.AnalysisSeverity severity,
    List<String> insufficientCodes,
    List<BalancingContract.Metric> metrics
) {
    public EconomicMetricEvaluation {
        insufficientCodes = List.copyOf(insufficientCodes);
        metrics = List.copyOf(metrics);
    }

    public boolean sufficient() {
        return sample.status() == BalancingContract.SampleStatus.SUFFICIENT;
    }

    public static BalancingContract.Period period(
        Instant from,
        Instant to,
        Instant dataAsOf
    ) {
        return new BalancingContract.Period(from, to, dataAsOf);
    }
}
