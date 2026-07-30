package com.b201.ecovillage.course.api;

import java.security.Principal;
import java.time.Clock;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.course.application.CourseRecommendationQueryService;
import com.b201.ecovillage.identity.domain.CourseSize;

@RestController
public class CourseRecommendationController {

    private final CourseRecommendationQueryService queryService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public CourseRecommendationController(
        CourseRecommendationQueryService queryService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.queryService = queryService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @GetMapping("/course-recommendations")
    public ResponseEntity<ApiResponse<CourseRecommendationsResponse>> find(
        @RequestParam(required = false) Integer grade,
        @RequestParam(name = "course_size", required = false) CourseSize courseSize,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID resolvedCorrelationId = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        CourseRecommendationsResponse data = new CourseRecommendationsResponse(
            queryService.find(subject(principal), grade, courseSize)
                .stream()
                .map(view -> new CourseRecommendationResponse(
                    view.recommendedCourseId(),
                    view.templateCode(),
                    view.name(),
                    view.grade(),
                    view.courseSize(),
                    view.version(),
                    view.dataVersion(),
                    view.publishedAt(),
                    view.difficultyProfile(),
                    view.learningFocus(),
                    view.recommendationReason(),
                    view.modules().stream()
                        .map(module -> new CourseModuleResponse(
                            module.moduleCode(),
                            module.label(),
                            module.recommendationLevel(),
                            module.prerequisites(),
                            module.exclusionImpact()
                        ))
                        .toList()
                ))
                .toList()
        );
        return ResponseEntity.ok(
            new ApiResponse<>(
                data,
                new ApiMeta(requestId, resolvedCorrelationId, clock.instant())
            )
        );
    }

    private static String subject(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
