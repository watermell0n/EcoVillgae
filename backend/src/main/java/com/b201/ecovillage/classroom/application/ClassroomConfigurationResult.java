package com.b201.ecovillage.classroom.application;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

import com.b201.ecovillage.identity.domain.CourseSize;

public record ClassroomConfigurationResult(
    UUID classroomId,
    String classroomCode,
    String displayName,
    Integer classNumber,
    String description,
    String currencyName,
    int grade,
    CourseSize courseSize,
    int studentCount,
    LocalDate startsOn,
    LocalDate endsOn,
    UUID recommendedCourseId,
    String difficultyProfile,
    long stateVersion,
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
