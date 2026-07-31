package com.b201.ecovillage.identity.api;

import java.util.UUID;

public record SchoolAccessResponse(
    UUID schoolId,
    String role
) {
}
