package com.b201.ecovillage.classroom.api;

import java.security.Principal;
import java.time.Clock;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.classroom.application.StudentActivityQueryService;
import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;

@RestController
public class StudentActivityController {

    private final StudentActivityQueryService service;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public StudentActivityController(
        StudentActivityQueryService service,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.service = service;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @GetMapping("/classrooms/{classroomId}/student-activities")
    public ApiResponse<StudentActivitiesResponse> findRecent(
        @PathVariable UUID classroomId,
        @RequestParam(defaultValue = "100") int limit,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID correlation = correlationId == null ? uuidGenerator.generate() : correlationId;
        StudentActivitiesResponse response = new StudentActivitiesResponse(
            service.findRecent(
                principal == null ? null : principal.getName(),
                classroomId,
                limit
            ).stream().map(activity -> new StudentActivityResponse(
                activity.resourceId(),
                activity.studentId(),
                activity.activityType(),
                activity.title(),
                activity.quantity(),
                activity.amount(),
                activity.status(),
                activity.occurredAt()
            )).toList()
        );
        return new ApiResponse<>(response, new ApiMeta(requestId, correlation, clock.instant()));
    }
}
