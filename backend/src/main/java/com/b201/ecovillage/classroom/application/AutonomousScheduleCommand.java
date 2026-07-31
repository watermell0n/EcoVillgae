package com.b201.ecovillage.classroom.application;

import java.time.LocalTime;
import java.util.List;

public record AutonomousScheduleCommand(
    int dayOfWeek,
    LocalTime localStartTime,
    LocalTime localEndTime,
    List<String> moduleCodes
) {
}
