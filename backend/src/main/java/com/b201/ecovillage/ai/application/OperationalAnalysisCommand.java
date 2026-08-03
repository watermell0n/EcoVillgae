package com.b201.ecovillage.ai.application;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.b201.ecovillage.ai.contract.BalancingContract;

public record OperationalAnalysisCommand(
    String teacherSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    BalancingContract.AnalysisMode analysisMode,
    Instant periodFrom,
    Instant periodTo,
    Set<BalancingContract.MetricCode> metricCodes
) {
    public OperationalAnalysisCommand {
        metricCodes = metricCodes == null ? Set.of() : Set.copyOf(metricCodes);
    }
}
