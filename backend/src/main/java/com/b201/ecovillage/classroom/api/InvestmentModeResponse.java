package com.b201.ecovillage.classroom.api;

import java.util.List;
import java.util.UUID;

public record InvestmentModeResponse(
    UUID moduleId,
    String moduleCode,
    String status,
    List<String> investmentModes,
    long stateVersion,
    boolean replayed
) {
}
