package com.b201.bibim.feature.result;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.b201.bibim.feature.result.FinalResultDto.ProductDistributionResponse;
import com.b201.bibim.feature.result.FinalResultDto.TeacherFinalSummaryResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teacher/rooms/{roomId}/results")
public class TeacherFinalResultController {

    private final FinalResultService service;

    public TeacherFinalResultController(FinalResultService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    ApiResponse<TeacherFinalSummaryResponse> summary(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                service.teacherSummary(principal.id(), roomId),
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME)
        );
    }

    @GetMapping("/product-distribution")
    ApiResponse<ProductDistributionResponse> distribution(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                service.productDistribution(principal.id(), roomId),
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME)
        );
    }
}

