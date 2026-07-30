package com.b201.ecovillage.quiz.application;

import java.util.UUID;

public record GuidedActivityView(
    UUID activityId,
    String status,
    long participantCount
) {
}
