package com.b201.ecovillage.classroom.application;

import java.util.List;
import java.util.UUID;

public record ClassroomConfigurationResult(
    UUID classroomId,
    String classroomCode,
    String currencyName,
    List<String> moduleCodes,
    List<ScheduleResult> autonomousSchedules,
    List<PolicyVersionResult> policyVersions
) {
    public record ScheduleResult(
        UUID resourceId,
        String label,
        String status,
        int version,
        long stateVersion,
        List<String> moduleCodes
    ) {
    }

    public record PolicyVersionResult(
        UUID resourceId,
        String label,
        String status,
        int version,
        long stateVersion,
        String policyArea
    ) {
    }
}
