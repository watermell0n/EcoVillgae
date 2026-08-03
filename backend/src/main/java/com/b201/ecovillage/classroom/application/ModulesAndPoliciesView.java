package com.b201.ecovillage.classroom.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ModulesAndPoliciesView(
    List<ModuleView> modules,
    List<PolicyVersionView> policyVersions
) {
    public record ModuleView(
        UUID resourceId,
        String label,
        String status,
        int version,
        long stateVersion,
        String moduleCode,
        Map<String, Object> config
    ) {
    }

    public record PolicyVersionView(
        UUID resourceId,
        String label,
        String status,
        int version,
        long stateVersion,
        String policyArea
    ) {
    }
}
