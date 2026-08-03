package com.b201.ecovillage.ruleengine.domain;

import java.util.List;

public record RuleCheck(
    String checkId,
    RuleSeverity severity,
    String reasonCode,
    List<String> subjectActionIds,
    String message
) {
    public RuleCheck {
        subjectActionIds = List.copyOf(subjectActionIds);
    }
}
