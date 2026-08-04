package com.b201.ecovillage.classroom.application;

import java.util.UUID;

public record ModuleActivationResult(
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
