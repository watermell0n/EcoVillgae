package com.b201.ecovillage.classroom.api;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AccessModeResponse(
    String mode,
    String source,
    @JsonProperty("expires_at") Instant expiresAt,
    @JsonProperty("module_codes") List<String> moduleCodes,
    @JsonProperty("state_version") long stateVersion
) {
}
