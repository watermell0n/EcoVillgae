package com.b201.bibim.feature.day1;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.b201.bibim.feature.day1.Day1Dto.ClosePreviewResponse;
import com.b201.bibim.feature.day1.Day1Dto.TeacherDay1SummaryResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teacher/rooms/{roomId}/day-1")
public class TeacherDay1Controller {

    private final Day1Service day1Service;

    public TeacherDay1Controller(Day1Service day1Service) {
        this.day1Service = day1Service;
    }

    @GetMapping("/summary")
    ApiResponse<TeacherDay1SummaryResponse> summary(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                day1Service.teacherSummary(principal.id(), roomId),
                requestId(request)
        );
    }

    @GetMapping("/close-preview")
    ApiResponse<ClosePreviewResponse> closePreview(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                day1Service.closePreview(principal.id(), roomId),
                requestId(request)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
