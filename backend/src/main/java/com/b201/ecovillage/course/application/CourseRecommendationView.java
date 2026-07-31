package com.b201.ecovillage.course.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.b201.ecovillage.identity.domain.CourseSize;

public record CourseRecommendationView(
    UUID recommendedCourseId,
    String templateCode,
    String name,
    int grade,
    CourseSize courseSize,
    int version,
    String dataVersion,
    Instant publishedAt,
    Map<String, Object> difficultyProfile,
    String learningFocus,
    String recommendationReason,
    List<CourseModuleView> modules
) {
}
