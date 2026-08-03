package com.b201.ecovillage.ai.application;

import java.util.Map;
import java.util.UUID;

record SetupProposalTargetSnapshot(
    String contextType,
    String targetType,
    UUID targetId,
    long targetVersion,
    int primaryPolicyVersion,
    Map<String, Object> snapshot,
    String fingerprint
) {
    SetupProposalTargetSnapshot {
        snapshot = Map.copyOf(snapshot);
    }
}
