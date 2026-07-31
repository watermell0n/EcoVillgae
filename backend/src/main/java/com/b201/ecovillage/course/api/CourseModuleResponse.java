package com.b201.ecovillage.course.api;

import java.util.List;

public record CourseModuleResponse(
    String moduleCode,
    String label,
    String recommendationLevel,
    List<String> prerequisites,
    String exclusionImpact
) {
}
