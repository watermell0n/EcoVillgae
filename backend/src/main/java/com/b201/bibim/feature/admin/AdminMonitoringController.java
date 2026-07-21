package com.b201.bibim.feature.admin;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.b201.bibim.feature.admin.AdminMonitoringDto.ActivityResponse;
import com.b201.bibim.feature.admin.AdminMonitoringDto.RoomMonitoringResponse;
import com.b201.bibim.feature.admin.AdminMonitoringDto.RoomStudentsResponse;
import com.b201.bibim.feature.admin.AdminMonitoringDto.RoomFinanceReportResponse;
import com.b201.bibim.feature.admin.AdminMonitoringDto.SessionTerminationRequest;
import com.b201.bibim.feature.admin.AdminMonitoringDto.SessionTerminationResponse;
import com.b201.bibim.feature.admin.AdminMonitoringDto.ForceStudentViewRequest;
import com.b201.bibim.feature.admin.AdminMonitoringDto.WalletAdjustmentRequest;
import com.b201.bibim.feature.admin.AdminMonitoringDto.WalletAdjustmentResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;
import com.b201.bibim.feature.flow.StudentFlowDto.FlowResponse;
import com.b201.bibim.feature.flow.StudentFlowService;
import com.b201.bibim.feature.room.RoomSessionDto.CommandRequest;
import com.b201.bibim.feature.room.RoomSessionDto.CommandResponse;
import com.b201.bibim.feature.room.RoomSessionService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminMonitoringController {

    private final AdminMonitoringService adminMonitoringService;
    private final RoomSessionService roomSessionService;
    private final StudentFlowService studentFlowService;

    public AdminMonitoringController(
            AdminMonitoringService adminMonitoringService,
            RoomSessionService roomSessionService,
            StudentFlowService studentFlowService
    ) {
        this.adminMonitoringService = adminMonitoringService;
        this.roomSessionService = roomSessionService;
        this.studentFlowService = studentFlowService;
    }

    @PostMapping("/rooms/{roomId}/commands")
    ApiResponse<CommandResponse> command(
            @PathVariable UUID roomId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CommandRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                roomSessionService.commandAsAdmin(roomId, idempotencyKey, body),
                requestId(request)
        );
    }

    @PostMapping("/rooms/{roomId}/students/{studentId}/force-current-stage")
    ApiResponse<FlowResponse> forceCurrentStage(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @PathVariable UUID studentId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                studentFlowService.forceMoveToCurrentStage(principal.id(), roomId, studentId),
                requestId(request)
        );
    }

    @PostMapping("/rooms/{roomId}/students/{studentId}/force-view")
    ApiResponse<FlowResponse> forceView(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @PathVariable UUID studentId,
            @Valid @RequestBody ForceStudentViewRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                studentFlowService.forceMoveToStage(principal.id(), roomId, studentId, body.targetStage()),
                requestId(request)
        );
    }

    @PostMapping("/rooms/{roomId}/students/{studentId}/wallet-adjustments")
    ApiResponse<WalletAdjustmentResponse> adjustWallet(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @PathVariable UUID studentId,
            @Valid @RequestBody WalletAdjustmentRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                adminMonitoringService.adjustWallet(
                        principal.id(), roomId, studentId, body.amount(), body.reason()
                ),
                requestId(request)
        );
    }

    @DeleteMapping("/rooms/{roomId}")
    ApiResponse<RoomMonitoringResponse> archiveRoom(
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(adminMonitoringService.archiveRoom(roomId), requestId(request));
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

    @GetMapping("/rooms/{roomId}/finance-report")
    ApiResponse<RoomFinanceReportResponse> financeReport(
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(adminMonitoringService.financeReport(roomId), requestId(request));
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
