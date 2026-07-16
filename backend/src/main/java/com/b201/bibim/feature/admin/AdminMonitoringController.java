package com.b201.bibim.feature.admin;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.b201.bibim.feature.admin.AdminMonitoringDto.ActivityResponse;
import com.b201.bibim.feature.admin.AdminMonitoringDto.RoomMonitoringResponse;
import com.b201.bibim.feature.admin.AdminMonitoringDto.RoomStudentsResponse;
import com.b201.bibim.feature.admin.AdminMonitoringDto.SessionTerminationRequest;
import com.b201.bibim.feature.admin.AdminMonitoringDto.SessionTerminationResponse;
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
@RequestMapping("/api/v1/admin")
public class AdminMonitoringController {

    private final AdminMonitoringService adminMonitoringService;

    public AdminMonitoringController(AdminMonitoringService adminMonitoringService) {
        this.adminMonitoringService = adminMonitoringService;
    }

    @GetMapping("/rooms")
    ApiResponse<List<RoomMonitoringResponse>> rooms(HttpServletRequest request) {
        return ApiResponse.success(adminMonitoringService.rooms(), requestId(request));
    }

    @GetMapping("/rooms/{roomId}/students")
    ApiResponse<RoomStudentsResponse> students(
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(adminMonitoringService.students(roomId), requestId(request));
    }

    @GetMapping("/rooms/{roomId}/students/{studentId}/activities")
    ApiResponse<List<ActivityResponse>> activities(
            @PathVariable UUID roomId,
            @PathVariable UUID studentId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                adminMonitoringService.activities(roomId, studentId),
                requestId(request)
        );
    }

    @PostMapping("/rooms/{roomId}/students/{studentId}/session-termination")
    ApiResponse<SessionTerminationResponse> terminateSession(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @PathVariable UUID studentId,
            @Valid @RequestBody SessionTerminationRequest terminationRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                adminMonitoringService.terminateStudentSession(
                        principal.id(),
                        roomId,
                        studentId,
                        terminationRequest.reason()
                ),
                requestId(request)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
