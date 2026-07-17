package com.b201.bibim.feature.settlement;

import jakarta.servlet.http.HttpServletRequest;

import com.b201.bibim.feature.settlement.Day2QuizRewardDto.StudentRewardResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student/day-2/quiz-reward")
public class StudentDay2QuizRewardController {

    private final Day2QuizRewardService service;

    public StudentDay2QuizRewardController(Day2QuizRewardService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<StudentRewardResponse> result(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                service.studentResult(principal.id()),
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME)
        );
    }
}
