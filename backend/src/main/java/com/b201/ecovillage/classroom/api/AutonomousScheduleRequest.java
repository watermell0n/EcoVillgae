package com.b201.ecovillage.classroom.api;

import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AutonomousScheduleRequest(
    @Min(1) @Max(7) int dayOfWeek,
    @NotNull LocalTime localStartTime,
    @NotNull LocalTime localEndTime,
    List<String> moduleCodes
) {
}
