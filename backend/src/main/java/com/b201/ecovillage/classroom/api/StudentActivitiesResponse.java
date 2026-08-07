package com.b201.ecovillage.classroom.api;

import java.util.List;

public record StudentActivitiesResponse(List<StudentActivityResponse> activities) {
    public StudentActivitiesResponse {
        activities = List.copyOf(activities);
    }
}
