package com.b201.ecovillage.identity.application;

import java.util.List;
import java.util.UUID;

public record AuthBootstrapView(
    UUID userId,
    List<String> roles,
    List<ClassroomView> classrooms,
    String startScreen
) {
    public record ClassroomView(
        UUID resourceId,
        String label,
        String status,
        int version,
        long stateVersion,
        String classroomCode,
        String currencyName
    ) {
    }
}
