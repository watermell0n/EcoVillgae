package com.b201.ecovillage.classroom.api;

import java.util.UUID;

public record EconomicPolicyResponse(
    UUID policyVersionId,
    int policyVersion,
    String status,
    long stateVersion,
    boolean replayed
) {
}
