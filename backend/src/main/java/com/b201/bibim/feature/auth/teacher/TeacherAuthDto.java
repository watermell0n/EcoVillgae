package com.b201.bibim.feature.auth.teacher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class TeacherAuthDto {

    private TeacherAuthDto() {
    }

    public record LoginRequest(
            @NotBlank(message = "아이디를 입력해 주세요.")
            @Size(max = 100, message = "아이디가 너무 깁니다.")
            String loginId,
            @NotBlank(message = "비밀번호를 입력해 주세요.")
            @Size(max = 100, message = "비밀번호가 너무 깁니다.")
            String password
    ) {
    }

    public record TeacherResponse(
            UUID teacherId,
            String displayName,
            List<RoomSummary> rooms
    ) {
    }

    public record RoomSummary(
            UUID roomId,
            String name,
            int expectedStudentCount,
            long connectedStudentCount,
            String status,
            int currentDay,
            String currentStage,
            String mode,
            long syncVersion,
            Instant updatedAt
    ) {
    }
}
