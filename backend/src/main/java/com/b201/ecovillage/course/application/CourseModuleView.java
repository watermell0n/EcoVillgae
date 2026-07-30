package com.b201.ecovillage.course.application;

import java.util.List;

public record CourseModuleView(
    String moduleCode,
    String label,
    String recommendationLevel,
    List<String> prerequisites,
    String exclusionImpact
) {
}
