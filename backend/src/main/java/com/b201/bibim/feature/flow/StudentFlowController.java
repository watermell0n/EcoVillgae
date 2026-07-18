package com.b201.bibim.feature.flow;

import jakarta.servlet.http.HttpServletRequest;

import com.b201.bibim.feature.flow.StudentFlowDto.FlowResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student/flow")
public class StudentFlowController {

    private final StudentFlowService flowService;

    public StudentFlowController(StudentFlowService flowService) {
        this.flowService = flowService;
    }

    @GetMapping
    ApiResponse<FlowResponse> current(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(flowService.current(principal.id()), requestId(request));
    }

    @PostMapping("/advance")
    ApiResponse<FlowResponse> advance(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(flowService.advance(principal.id()), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
