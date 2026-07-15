package com.b201.bibim.feature.room;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.b201.bibim.feature.room.RoomSessionDto.CommandRequest;
import com.b201.bibim.feature.room.RoomSessionDto.CommandResponse;
import com.b201.bibim.feature.room.RoomSessionDto.TeacherRoomStateResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teacher/rooms/{roomId}")
public class TeacherRoomSessionController {

    private final RoomSessionService roomSessionService;

    public TeacherRoomSessionController(RoomSessionService roomSessionService) {
        this.roomSessionService = roomSessionService;
    }

    @GetMapping("/state")
    ApiResponse<TeacherRoomStateResponse> state(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @RequestParam(required = false) Long syncVersion,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                roomSessionService.teacherState(principal.id(), roomId, syncVersion),
                requestId(request)
        );
    }

    @PostMapping("/commands")
    ApiResponse<CommandResponse> command(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CommandRequest commandRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                roomSessionService.command(
                        principal.id(),
                        roomId,
                        idempotencyKey,
                        commandRequest
                ),
                requestId(request)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
