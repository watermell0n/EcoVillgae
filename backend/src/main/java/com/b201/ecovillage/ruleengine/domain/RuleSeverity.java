package com.b201.ecovillage.ruleengine.domain;

public enum RuleSeverity {
    PASS(0),
    WARN(1),
    BLOCK(2);

    private final int priority;

    RuleSeverity(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
