package com.b201.ecovillage.identity.authorization;

import java.util.Set;

public enum ClassroomPermission {
    READ_CONFIGURATION(
        "PM-RBAC-001",
        Set.of(ClassroomActorRole.OWN, ClassroomActorRole.COT)
    ),
    MANAGE_ROSTER(
        "PM-CLS-004",
        Set.of(ClassroomActorRole.OWN)
    ),
    MANAGE_PAYOUTS(
        "PM-TXN-002",
        Set.of(ClassroomActorRole.OWN, ClassroomActorRole.COT)
    ),
    PURCHASE_STORE(
        "PM-SHP-002",
        Set.of(ClassroomActorRole.STU)
    ),
    PROPOSE_TRADE(
        "PM-TRD-001",
        Set.of(ClassroomActorRole.STU)
    ),
    DECIDE_TRADE(
        "PM-TRD-002",
        Set.of(ClassroomActorRole.STU)
    ),
    APPROVE_TRADE(
        "PM-TRD-003",
        Set.of(ClassroomActorRole.OWN, ClassroomActorRole.COT)
    ),
    MANAGE_FINANCIAL_PRODUCTS(
        "PM-FIN-001",
        Set.of(ClassroomActorRole.OWN)
    ),
    USE_FINANCIAL_PRODUCTS(
        "PM-FIN-002",
        Set.of(ClassroomActorRole.STU)
    ),
    READ_OWN_STUDENT_DATA(
        "PM-DAT-001",
        Set.of(ClassroomActorRole.STU)
    );

    private final String ruleId;
    private final Set<ClassroomActorRole> allowedRoles;

    ClassroomPermission(String ruleId, Set<ClassroomActorRole> allowedRoles) {
        this.ruleId = ruleId;
        this.allowedRoles = Set.copyOf(allowedRoles);
    }

    public String getRuleId() {
        return ruleId;
    }

    public boolean allows(ClassroomActorRole role) {
        return allowedRoles.contains(role);
    }
}
