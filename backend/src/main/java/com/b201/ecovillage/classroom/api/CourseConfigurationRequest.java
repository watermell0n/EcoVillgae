package com.b201.ecovillage.classroom.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.b201.ecovillage.identity.domain.CourseSize;

public record CourseConfigurationRequest(
    @Min(0) Long expectedStateVersion,
    @Pattern(regexp = "^SCR-[A-Z]+-\\d{3}$") String screenId,
    @Pattern(regexp = "^UIA-[A-Z]+-\\d{3}$") String uiActionId,
    Instant clientOccurredAt,
    @NotNull @Min(4) @Max(6) Integer grade,
    @NotNull CourseSize courseSize,
    @NotNull LocalDate startsOn,
    @NotNull LocalDate endsOn,
    UUID recommendedCourseId,
    @NotNull @Pattern(
        regexp = "GRADE_(4_BASIC|5_STANDARD|6_ADVANCED)"
    ) String difficultyProfile,
    @NotNull @Min(0) Integer expectedPolicyVersion,
    String classroomCode,
    String currencyName
) {
}
