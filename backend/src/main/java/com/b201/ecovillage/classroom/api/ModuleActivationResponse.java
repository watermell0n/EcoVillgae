package com.b201.ecovillage.classroom.api;

import java.util.UUID;

public record ModuleActivationResponse(
    String moduleCode,
    String status,
    long stateVersion,
    String policyArea,
    Integer policyVersion,
    UUID policyVersionId,
    String ruleVersion,
    boolean replayed
) {
}
