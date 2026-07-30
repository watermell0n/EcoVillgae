package com.b201.ecovillage.classroom.application;

import java.util.List;
import java.util.UUID;

public record UpdateRosterCommand(
    String teacherSubject,
    UUID classroomId,
    List<StudentRosterCommand> students,
    List<SalaryRoleCommand> salaryRoles,
    long expectedPolicyVersion,
    Long expectedStateVersion
) {
}
