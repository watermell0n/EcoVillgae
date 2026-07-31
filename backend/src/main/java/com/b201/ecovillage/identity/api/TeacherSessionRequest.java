package com.b201.ecovillage.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeacherSessionRequest(
    @NotBlank
    @Size(max = 20000)
    String idToken
) {
}
