package com.b201.ecovillage.ai.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.b201.ecovillage.ai.application.AiProposalCommandType;

public record AiProposalCommandRequest(
    @NotNull AiProposalCommandType command,
    @Valid @Size(max = 100) List<EditedValue> editedValues,
    @NotBlank @Size(max = 1000) String reason,
    Integer expectedPolicyVersion
) {
    public AiProposalCommandRequest {
        editedValues = editedValues == null ? List.of() : List.copyOf(editedValues);
    }

    public record EditedValue(
        @NotBlank String actionId,
        @NotNull Long proposedValue
    ) {
    }
}
