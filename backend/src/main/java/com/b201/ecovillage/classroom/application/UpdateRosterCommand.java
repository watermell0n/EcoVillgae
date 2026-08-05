package com.b201.ecovillage.classroom.application;

import java.util.List;
import java.util.UUID;

public record UpdateRosterCommand(
    String teacherSubject,
    UUID classroomId,
    List<StudentRosterCommand> students,
    List<SalaryRoleCommand> salaryRoles,
    Long expectedPolicyVersion,
    Long expectedStateVersion,
    /** true면 요청에 없는 학생과 급여 역할을 내립니다. 자세한 규칙은 RosterAndSalariesRequest에 있습니다. */
    boolean replaceRoster
) {
}
