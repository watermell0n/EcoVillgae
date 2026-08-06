package com.b201.ecovillage.classroom.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.b201.ecovillage.identity.domain.CourseSize;

public record CreateClassroomRequest(
    @Min(0) Long expectedStateVersion,
    @Pattern(regexp = "^SCR-[A-Z]+-\\d{3}$") String screenId,
    @Pattern(regexp = "^UIA-[A-Z]+-\\d{3}$") String uiActionId,
    Instant clientOccurredAt,
    @NotNull UUID schoolId,
    @Size(max = 120) String displayName,
    @Min(1) @Max(99) Integer classNumber,
    @Size(max = 500) String description,
    @NotBlank @Size(max = 10) String currencyName,
    @Min(4) @Max(6) int grade,
    @NotNull CourseSize courseSize,
    @NotNull LocalDate startsOn,
    @NotNull LocalDate endsOn,
    @Min(1) int studentCount,
    @NotBlank String timezone,
    Map<String, Object> salaryPolicy,
    @NotNull List<@NotBlank String> moduleCodes,
    List<@Valid AutonomousScheduleRequest> autonomousSchedules
) {
}
