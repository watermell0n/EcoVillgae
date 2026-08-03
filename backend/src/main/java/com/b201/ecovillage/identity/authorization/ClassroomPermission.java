package com.b201.ecovillage.identity.authorization;

import java.util.Set;

public enum ClassroomPermission {
    READ_CONFIGURATION(
        "PM-RBAC-001",
        Set.of(ClassroomActorRole.OWN, ClassroomActorRole.COT)
    ),
    READ_ROSTER(
        "PM-CLS-004",
        Set.of(ClassroomActorRole.OWN, ClassroomActorRole.COT)
    ),
    READ_MODULES_AND_POLICIES(
        "PM-RBAC-002",
        Set.of(
            ClassroomActorRole.STU,
            ClassroomActorRole.OWN,
            ClassroomActorRole.COT
        )
    ),
    MANAGE_ROSTER(
        "PM-CLS-004",
        Set.of(ClassroomActorRole.OWN)
    ),
    MANAGE_COURSE_CONFIGURATION(
        "PM-CLS-005",
        Set.of(ClassroomActorRole.OWN)
    ),
    MANAGE_MODULES(
        "PM-MOD-001",
        Set.of(ClassroomActorRole.OWN)
    ),
    MANAGE_ECONOMIC_POLICY(
        "PM-POL-001",
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
    MANAGE_QUIZ_QUESTIONS(
        "PM-QZ-001",
        Set.of(ClassroomActorRole.OWN, ClassroomActorRole.COT)
    ),
    MANAGE_QUIZ_SESSIONS(
        "PM-QZ-002",
        Set.of(ClassroomActorRole.OWN, ClassroomActorRole.COT)
    ),
    CONTROL_CLASSROOM_ACCESS(
        "PM-CLS-008",
        Set.of(ClassroomActorRole.OWN, ClassroomActorRole.COT)
    ),
    MANAGE_GUIDED_ACTIVITIES(
        "PM-ACT-001",
        Set.of(ClassroomActorRole.OWN, ClassroomActorRole.COT)
    ),
    READ_GUIDED_ACTIVITY(
        "PM-ACT-001",
        Set.of(
            ClassroomActorRole.STU,
            ClassroomActorRole.OWN,
            ClassroomActorRole.COT
        )
    ),
    CONTROL_PUBLIC_DISPLAY(
        "PM-DSP-001",
        Set.of(ClassroomActorRole.OWN, ClassroomActorRole.COT)
    ),
    READ_AUDIT_EVENTS(
        "PM-DAT-005",
        Set.of(ClassroomActorRole.OWN, ClassroomActorRole.COT)
    ),
    READ_CLASSROOM_REPORT(
        "PM-DAT-002",
        Set.of(ClassroomActorRole.OWN, ClassroomActorRole.COT)
    ),
    REQUEST_AI_ANALYSIS(
        "PM-AI-003",
        Set.of(ClassroomActorRole.OWN, ClassroomActorRole.COT)
    ),
    ACKNOWLEDGE_AI_PROPOSAL(
        "PM-AI-004",
        Set.of(ClassroomActorRole.OWN, ClassroomActorRole.COT)
    ),
    APPLY_AI_PROPOSAL(
        "PM-AI-004",
        Set.of(ClassroomActorRole.OWN)
    ),
    MANAGE_AI_SETTINGS(
        "PM-AI-005",
        Set.of(ClassroomActorRole.OWN)
    ),
    MANAGE_PUBLIC_FUND_VOTE(
        "PM-ACT-002",
        Set.of(ClassroomActorRole.OWN, ClassroomActorRole.COT)
    ),
    READ_PUBLIC_FUND_VOTE(
        "PM-ACT-001",
        Set.of(
            ClassroomActorRole.STU,
            ClassroomActorRole.OWN,
            ClassroomActorRole.COT
        )
    ),
    CAST_PUBLIC_FUND_VOTE(
        "PM-ACT-001",
        Set.of(ClassroomActorRole.STU)
    ),
    PARTICIPATE_QUIZ(
        "PM-QZ-002",
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
