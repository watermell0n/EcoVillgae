package com.b201.ecovillage.classroom.application;

import java.time.Instant;
import java.util.List;

public record AccessModeResult(
    AppAccessMode mode,
    String source,
    Instant expiresAt,
    List<String> moduleCodes,
    long stateVersion
) {
    public AccessModeResult {
        moduleCodes = List.copyOf(moduleCodes);
    }
}
