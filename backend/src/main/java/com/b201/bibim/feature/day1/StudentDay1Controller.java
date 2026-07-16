package com.b201.bibim.feature.day1;

import jakarta.servlet.http.HttpServletRequest;

import com.b201.bibim.feature.day1.Day1Dto.StudentDay1SummaryResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student/day-1")
public class StudentDay1Controller {

    private final Day1Service day1Service;

    public StudentDay1Controller(Day1Service day1Service) {
        this.day1Service = day1Service;
    }

    @GetMapping("/summary")
    ApiResponse<StudentDay1SummaryResponse> summary(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(day1Service.studentSummary(principal.id()), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
