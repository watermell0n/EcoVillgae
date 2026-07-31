package com.b201.ecovillage.course.api;

import java.util.List;

public record CourseRecommendationsResponse(
    List<CourseRecommendationResponse> recommendations
) {
}
