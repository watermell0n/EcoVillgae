package com.b201.bibim.feature.learning;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.b201.bibim.feature.learning.LearningDto.ChangePageRequest;
import com.b201.bibim.feature.learning.LearningDto.GuidedLearningResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teacher/rooms/{roomId}/learning")
public class TeacherLearningController {

    private final LearningService learningService;

    public TeacherLearningController(LearningService learningService) {
        this.learningService = learningService;
    }

    @GetMapping
    ApiResponse<GuidedLearningResponse> current(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                learningService.teacherCurrent(principal.id(), roomId),
                requestId(request)
        );
    }

    @PutMapping("/page")
    ApiResponse<GuidedLearningResponse> changePage(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @Valid @RequestBody ChangePageRequest changePageRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                learningService.changePage(principal.id(), roomId, changePageRequest),
                requestId(request)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
