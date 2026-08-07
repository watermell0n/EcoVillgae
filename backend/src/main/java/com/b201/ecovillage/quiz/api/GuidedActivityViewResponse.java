package com.b201.ecovillage.quiz.api;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GuidedActivityViewResponse(
    @JsonProperty("activity_id") UUID activityId,
    String status,
    @JsonProperty("state_version") long stateVersion,
    @JsonProperty("participant_count") long participantCount,
    @JsonProperty("activity_type") String activityType,
    @JsonProperty("presentation_title") String presentationTitle,
    @JsonProperty("presentation_image_url") String presentationImageUrl,
    @JsonProperty("presentation_page") int presentationPage,
    @JsonProperty("presentation_total_pages") int presentationTotalPages
) {
}
