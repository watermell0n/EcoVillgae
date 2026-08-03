package com.b201.ecovillage.classroom.application;

import java.time.LocalDate;
import java.util.UUID;

import com.b201.ecovillage.identity.domain.CourseSize;

public record CourseConfigurationCommand(
    String teacherSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    int grade,
    CourseSize courseSize,
    LocalDate startsOn,
    LocalDate endsOn,
    UUID recommendedCourseId,
    String difficultyProfile,
    int expectedPolicyVersion,
    String classroomCode,
    String currencyName
) {
}
