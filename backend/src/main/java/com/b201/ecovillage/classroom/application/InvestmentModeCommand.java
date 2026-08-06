package com.b201.ecovillage.classroom.application;

import java.util.List;
import java.util.UUID;

public record InvestmentModeCommand(
    String teacherSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    Long expectedStateVersion,
    List<String> investmentModes
) {
}
