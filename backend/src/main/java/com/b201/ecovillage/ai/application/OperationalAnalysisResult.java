package com.b201.ecovillage.ai.application;

import java.util.List;
import java.util.UUID;

import com.b201.ecovillage.ai.contract.BalancingContract;

public record OperationalAnalysisResult(
    UUID operationId,
    UUID aiRunId,
    String operationStatus,
    String runStatus,
    BalancingContract.AnalysisMode analysisMode,
    BalancingContract.AnalysisSeverity analysisSeverity,
    BalancingContract.Sample sample,
    List<String> insufficientCodes,
    List<BalancingContract.Metric> metrics,
    UUID proposalId,
    boolean fallbackUsed,
    String fallbackCode,
    String summary
) {
    public OperationalAnalysisResult {
        insufficientCodes = List.copyOf(insufficientCodes);
        metrics = List.copyOf(metrics);
    }
}
