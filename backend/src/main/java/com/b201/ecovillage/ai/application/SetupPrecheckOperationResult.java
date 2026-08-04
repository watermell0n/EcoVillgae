package com.b201.ecovillage.ai.application;

import java.util.List;
import java.util.UUID;

import com.b201.ecovillage.ai.contract.SetupPrecheckContract;

public record SetupPrecheckOperationResult(
    UUID operationId,
    UUID aiRunId,
    String operationStatus,
    String runStatus,
    boolean fallbackUsed,
    String fallbackCode,
    SetupPrecheckContract.Severity overallSeverity,
    UUID proposalId,
    String summary,
    List<SetupPrecheckContract.CandidateExplanation> candidates,
    List<SetupPrecheckContract.CheckExplanation> checks
) {
    public SetupPrecheckOperationResult {
        candidates = List.copyOf(candidates);
        checks = List.copyOf(checks);
    }
}
