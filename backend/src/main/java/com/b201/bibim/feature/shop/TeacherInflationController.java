package com.b201.bibim.feature.shop;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.b201.bibim.feature.shop.InflationDto.ApplyResponse;
import com.b201.bibim.feature.shop.InflationDto.PreviewResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teacher/rooms/{roomId}/inflation")
public class TeacherInflationController {

    private final InflationService service;

    public TeacherInflationController(InflationService service) {
        this.service = service;
    }

    @GetMapping("/preview")
    ApiResponse<PreviewResponse> preview(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                service.preview(principal.id(), roomId),
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME)
        );
    }

    @PostMapping
    ApiResponse<ApplyResponse> apply(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                service.apply(principal.id(), roomId),
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME)
        );
    }
}
