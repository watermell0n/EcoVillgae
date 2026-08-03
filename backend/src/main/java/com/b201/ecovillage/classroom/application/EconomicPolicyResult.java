package com.b201.ecovillage.classroom.application;

import java.util.UUID;

public record EconomicPolicyResult(
    UUID policyVersionId,
    int policyVersion,
    String status,
    long stateVersion,
    boolean replayed
) {
}
