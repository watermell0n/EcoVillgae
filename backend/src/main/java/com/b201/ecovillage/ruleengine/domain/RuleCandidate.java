package com.b201.ecovillage.ruleengine.domain;

import java.util.List;
import java.util.UUID;

public record RuleCandidate(
    String actionId,
    RuleTargetType targetType,
    UUID targetId,
    long proposedValue,
    long recommendedMinValue,
    long recommendedMaxValue,
    long allowedMinValue,
    long allowedMaxValue,
    RuleValueUnit unit,
    List<String> reasonCodes,
    String label
) {
    public RuleCandidate {
        reasonCodes = List.copyOf(reasonCodes);
        if (
            allowedMinValue > recommendedMinValue
                || recommendedMinValue > proposedValue
                || proposedValue > recommendedMaxValue
                || recommendedMaxValue > allowedMaxValue
        ) {
            throw new IllegalArgumentException("candidate ranges must be ordered");
        }
    }
}
