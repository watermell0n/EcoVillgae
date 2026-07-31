package com.b201.ecovillage.classroom.api;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StudentRosterRequest(
    UUID studentId,
    @Min(1) int attendanceNumber,
    @NotBlank @Size(max = 40) String nickname,
    @NotBlank String roleCode
) {
}
