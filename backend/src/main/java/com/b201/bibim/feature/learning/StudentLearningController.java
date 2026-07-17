package com.b201.bibim.feature.learning;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.b201.bibim.feature.learning.LearningDto.GuidedLearningResponse;
import com.b201.bibim.feature.learning.LearningDto.TopicDetailResponse;
import com.b201.bibim.feature.learning.LearningDto.TopicResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student/learning")
public class StudentLearningController {

    private final LearningService learningService;

    public StudentLearningController(LearningService learningService) {
        this.learningService = learningService;
    }

    @GetMapping("/current")
    ApiResponse<GuidedLearningResponse> current(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                learningService.studentCurrent(principal.id()),
                requestId(request)
        );
    }

    @GetMapping("/topics")
    ApiResponse<List<TopicResponse>> topics(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                learningService.studentTopics(principal.id()),
                requestId(request)
        );
    }

    @GetMapping("/topics/{topicId}")
    ApiResponse<TopicDetailResponse> topic(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID topicId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                learningService.studentTopic(principal.id(), topicId),
                requestId(request)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
