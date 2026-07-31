package com.b201.ecovillage.identity.application;

import java.util.UUID;

public record SchoolAccessView(
    UUID schoolId,
    String role
) {
}
