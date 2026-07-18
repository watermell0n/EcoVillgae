package com.b201.bibim.feature.flow;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.b201.bibim.feature.flow.StudentFlowDto.FlowResponse;
import com.b201.bibim.feature.flow.StudentFlowDto.OverrideRequest;
import com.b201.bibim.feature.flow.StudentFlowDto.TeacherFlowListResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teacher/rooms/{roomId}/student-flows")
public class TeacherStudentFlowController {

    private final StudentFlowService flowService;

    public TeacherStudentFlowController(StudentFlowService flowService) {
        this.flowService = flowService;
    }

    @GetMapping
    ApiResponse<TeacherFlowListResponse> flows(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                flowService.teacherFlows(principal.id(), roomId), requestId(request)
        );
    }

    @PostMapping("/{studentId}/approve")
    ApiResponse<FlowResponse> approve(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @PathVariable UUID studentId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                flowService.approveJoin(principal.id(), roomId, studentId), requestId(request)
        );
    }

    @PostMapping("/{studentId}/activities/{activityCode}/waive")
    ApiResponse<FlowResponse> waive(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @PathVariable UUID studentId,
            @PathVariable String activityCode,
            @Valid @RequestBody OverrideRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                flowService.waive(
                        principal.id(), roomId, studentId,
                        activityCode.trim().toUpperCase(), body.reason()
                ),
                requestId(request)
        );
    }

    @PostMapping("/{studentId}/activities/{activityCode}/reopen")
    ApiResponse<FlowResponse> reopen(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @PathVariable UUID studentId,
            @PathVariable String activityCode,
            @Valid @RequestBody OverrideRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                flowService.reopen(
                        principal.id(), roomId, studentId,
                        activityCode.trim().toUpperCase(), body.reason()
                ),
                requestId(request)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
