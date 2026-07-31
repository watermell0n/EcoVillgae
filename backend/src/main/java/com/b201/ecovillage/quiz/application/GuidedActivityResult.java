package com.b201.ecovillage.quiz.application;

import java.util.UUID;

public record GuidedActivityResult(
    UUID activityId,
    String status,
    long stateVersion
) {
}
