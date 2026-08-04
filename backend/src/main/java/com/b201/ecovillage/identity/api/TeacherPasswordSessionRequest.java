package com.b201.ecovillage.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeacherPasswordSessionRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(max = 72) String password
) {
}
