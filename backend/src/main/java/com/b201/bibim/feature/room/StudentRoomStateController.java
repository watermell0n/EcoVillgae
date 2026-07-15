package com.b201.bibim.feature.room;

import jakarta.servlet.http.HttpServletRequest;

import com.b201.bibim.feature.room.RoomSessionDto.StudentRoomStateResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student")
public class StudentRoomStateController {

    private final RoomSessionService roomSessionService;

    public StudentRoomStateController(RoomSessionService roomSessionService) {
        this.roomSessionService = roomSessionService;
    }

    @GetMapping("/room-state")
    ApiResponse<StudentRoomStateResponse> state(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam(required = false) Long syncVersion,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                roomSessionService.studentState(principal.id(), syncVersion),
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME)
        );
    }
}
