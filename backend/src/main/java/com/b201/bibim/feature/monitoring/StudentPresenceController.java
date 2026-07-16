package com.b201.bibim.feature.monitoring;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.b201.bibim.feature.monitoring.StudentPresenceDto.HeartbeatRequest;
import com.b201.bibim.feature.monitoring.StudentPresenceDto.HeartbeatResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student/presence")
public class StudentPresenceController {

    private final StudentPresenceService studentPresenceService;

    public StudentPresenceController(StudentPresenceService studentPresenceService) {
        this.studentPresenceService = studentPresenceService;
    }

    @PostMapping("/heartbeat")
    ApiResponse<HeartbeatResponse> heartbeat(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @Valid @RequestBody HeartbeatRequest heartbeatRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                studentPresenceService.heartbeat(principal.id(), heartbeatRequest),
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME)
        );
    }
}
