package com.b201.bibim.feature.result;

import jakarta.servlet.http.HttpServletRequest;

import com.b201.bibim.feature.result.FinalResultDto.StudentFinalResultResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student")
public class StudentFinalResultController {

    private final FinalResultService service;

    public StudentFinalResultController(FinalResultService service) {
        this.service = service;
    }

    @GetMapping("/final-result")
    ApiResponse<StudentFinalResultResponse> result(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                service.studentResult(principal.id()),
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME)
        );
    }
}

