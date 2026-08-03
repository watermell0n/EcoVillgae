package com.b201.ecovillage.classroom.api;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

public record RosterAndSalariesRequest(
    @Min(0) Long expectedStateVersion,
    @Pattern(regexp = "^SCR-[A-Z]+-\\d{3}$") String screenId,
    @Pattern(regexp = "^UIA-[A-Z]+-\\d{3}$") String uiActionId,
    Instant clientOccurredAt,
    @NotEmpty List<@Valid StudentRosterRequest> students,
    List<@Valid SalaryRoleRequest> salaryRoles,
    @Min(0) Long expectedPolicyVersion
) {
}
