package com.b201.ecovillage.ai.api;

import java.util.List;
import java.util.UUID;

import com.b201.ecovillage.ai.contract.SetupPrecheckContract;

public record SetupPrecheckOperationResponse(
    UUID operationId,
    UUID aiRunId,
    String operationStatus,
    String runStatus,
    boolean fallbackUsed,
    String fallbackCode,
    String overallSeverity,
    UUID proposalId,
    String summary,
    List<SetupPrecheckContract.CandidateExplanation> candidates,
    List<SetupPrecheckContract.CheckExplanation> checks
) {
    public SetupPrecheckOperationResponse {
        candidates = List.copyOf(candidates);
        checks = List.copyOf(checks);
    }
}
