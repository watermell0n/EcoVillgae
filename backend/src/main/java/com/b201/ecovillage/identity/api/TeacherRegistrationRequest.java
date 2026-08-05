package com.b201.ecovillage.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TeacherRegistrationRequest(
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{3,40}$") String schoolCode,
    @NotBlank @Size(max = 120) String displayName,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 4, max = 72) String password
) {
}
