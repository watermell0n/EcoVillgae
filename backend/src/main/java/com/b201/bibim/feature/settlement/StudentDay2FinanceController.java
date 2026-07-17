package com.b201.bibim.feature.settlement;

import jakarta.servlet.http.HttpServletRequest;

import com.b201.bibim.feature.settlement.Day2FinanceDto.StudentFinanceResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student/day-2")
public class StudentDay2FinanceController {

    private final Day2FinanceService day2FinanceService;

    public StudentDay2FinanceController(Day2FinanceService day2FinanceService) {
        this.day2FinanceService = day2FinanceService;
    }

    @GetMapping("/finance-result")
    ApiResponse<StudentFinanceResponse> result(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                day2FinanceService.studentResult(principal.id()),
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME)
        );
    }
}
