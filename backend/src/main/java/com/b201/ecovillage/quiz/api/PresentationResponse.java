package com.b201.ecovillage.quiz.api;

import java.util.UUID;

public record PresentationResponse(
    UUID activityId,
    String status,
    long stateVersion,
    int page,
    int totalPages,
    String title,
    String imageUrl,
    boolean hasPrevious,
    boolean hasNext
) {
}
