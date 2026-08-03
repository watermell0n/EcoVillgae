package com.b201.ecovillage.ruleengine.domain;

import java.util.List;

public record RuleEngineResult(
    String ruleVersion,
    RuleContextType contextType,
    RuleSeverity overallSeverity,
    List<RuleCandidate> candidates,
    List<RuleCheck> checks,
    RuleEvaluationMetrics metrics
) {
    public RuleEngineResult {
        candidates = List.copyOf(candidates);
        checks = List.copyOf(checks);
    }
}
