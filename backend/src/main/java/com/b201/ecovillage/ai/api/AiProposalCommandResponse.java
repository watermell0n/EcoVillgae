package com.b201.ecovillage.ai.api;

import java.util.List;
import java.util.UUID;

import com.b201.ecovillage.ai.application.AiProposalCommandResult;

public record AiProposalCommandResponse(
    UUID proposalId,
    UUID aiRunId,
    String decisionStatus,
    Integer policyVersion,
    UUID policyVersionId,
    List<AiProposalCommandResult.AppliedValue> appliedValues,
    long stateVersion
) {
    public AiProposalCommandResponse {
        appliedValues = List.copyOf(appliedValues);
    }
}
