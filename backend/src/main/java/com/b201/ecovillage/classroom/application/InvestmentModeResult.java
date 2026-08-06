package com.b201.ecovillage.classroom.application;

import java.util.List;
import java.util.UUID;

public record InvestmentModeResult(
    UUID moduleId,
    String moduleCode,
    String status,
    List<String> investmentModes,
    long stateVersion,
    boolean replayed
) {
}
