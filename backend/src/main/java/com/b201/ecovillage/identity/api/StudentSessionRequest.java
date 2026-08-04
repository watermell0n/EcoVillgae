package com.b201.ecovillage.identity.api;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StudentSessionRequest(
    @NotBlank
    @Size(max = 64)
    String classroomCode,

    @Min(1)
    Integer attendanceNumber,

    @NotBlank
    @Size(max = 40)
    String nickname,

    @NotBlank
    @Size(min = 4, max = 72)
    String password,

    @NotNull
    UUID deviceId
) {
}
