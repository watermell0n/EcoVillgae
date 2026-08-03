package com.b201.ecovillage.ai.application;

import java.util.List;
import java.util.UUID;

public record AiProposalCommandResult(
    UUID proposalId,
    UUID aiRunId,
    String decisionStatus,
    Integer policyVersion,
    UUID policyVersionId,
    List<AppliedValue> appliedValues,
    long stateVersion
) {
    public AiProposalCommandResult {
        appliedValues = List.copyOf(appliedValues);
    }

    public record AppliedValue(
        String actionId,
        long proposedValue,
        String unit
    ) {
    }
}
