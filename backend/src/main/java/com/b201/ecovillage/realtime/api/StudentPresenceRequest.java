package com.b201.ecovillage.realtime.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StudentPresenceRequest(
    @NotBlank
    @Pattern(regexp = "ONLINE|AWAY")
    String status,
    @JsonProperty("current_screen")
    @Size(max = 120)
    String currentScreen
) {
}
