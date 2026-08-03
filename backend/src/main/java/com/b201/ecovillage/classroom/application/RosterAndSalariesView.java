package com.b201.ecovillage.classroom.application;

import java.util.List;
import java.util.UUID;

public record RosterAndSalariesView(
    List<StudentView> students,
    List<SalaryRoleView> salaryRoles
) {
    public record StudentView(
        UUID resourceId,
        String label,
        String status,
        int version,
        long stateVersion,
        int attendanceNumber,
        String nickname,
        String roleCode
    ) {
    }

    public record SalaryRoleView(
        UUID resourceId,
        String label,
        String status,
        int version,
        long stateVersion,
        String roleCode,
        long grossAmount
    ) {
    }
}
