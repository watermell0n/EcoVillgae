package com.b201.ecovillage.classroom.api;

import java.time.Instant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.b201.ecovillage.classroom.application.ModuleStudentAccessCommandType;

public record ModuleStudentAccessRequest(
    @Min(0) Long expectedStateVersion,
    @Pattern(regexp = "^SCR-[A-Z]+-\\d{3}$") String screenId,
    @Pattern(regexp = "^UIA-[A-Z]+-\\d{3}$") String uiActionId,
    Instant clientOccurredAt,
    @NotNull ModuleStudentAccessCommandType command,
    @Size(max = 500) String reason
) {
}
