package com.b201.ecovillage.ai.application;

import java.util.Map;
import java.util.UUID;

public record AiProposalCommand(
    String teacherSubject,
    UUID classroomId,
    UUID proposalId,
    UUID requestId,
    UUID correlationId,
    AiProposalCommandType command,
    Map<String, Long> editedValues,
    String reason,
    Integer expectedPolicyVersion
) {
    public AiProposalCommand {
        editedValues = editedValues == null ? Map.of() : Map.copyOf(editedValues);
    }
}
