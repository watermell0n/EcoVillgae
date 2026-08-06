package com.b201.ecovillage.classroom.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.b201.ecovillage.identity.domain.CourseSize;

public record CreateClassroomCommand(
    String teacherSubject,
    UUID schoolId,
    String displayName,
    Integer classNumber,
    String description,
    String currencyName,
    int grade,
    CourseSize courseSize,
    LocalDate startsOn,
    LocalDate endsOn,
    int studentCount,
    String timeZone,
    Map<String, Object> salaryPolicy,
    List<String> moduleCodes,
    List<AutonomousScheduleCommand> autonomousSchedules,
    Long expectedStateVersion
) {
}
