package com.b201.ecovillage.identity.authorization;

import java.time.Instant;
import java.util.UUID;

public record AuthorizationDecision(
    String decision,
    String ruleId,
    ClassroomActorRole actorType,
    AuthorizationScope scope,
    UUID resourceId,
    long stateVersion,
    String reasonCode,
    Instant decidedAt
) {
}
