package com.b201.bibim.feature.room;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.b201.bibim.feature.room.TeacherRoomManagementDto.CreateRoomRequest;
import com.b201.bibim.feature.room.TeacherRoomManagementDto.RoomPreparationResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teacher/rooms")
public class TeacherRoomManagementController {

    private final TeacherRoomManagementService roomManagementService;

    public TeacherRoomManagementController(TeacherRoomManagementService roomManagementService) {
        this.roomManagementService = roomManagementService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<RoomPreparationResponse> create(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @Valid @RequestBody CreateRoomRequest createRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                roomManagementService.create(principal.id(), createRequest),
                requestId(request)
        );
    }

    @GetMapping("/{roomId}/students")
    ApiResponse<RoomPreparationResponse> students(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                roomManagementService.students(principal.id(), roomId),
                requestId(request)
        );
    }

    @DeleteMapping("/{roomId}")
    ApiResponse<RoomPreparationResponse> archive(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                roomManagementService.archive(principal.id(), roomId),
                requestId(request)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
