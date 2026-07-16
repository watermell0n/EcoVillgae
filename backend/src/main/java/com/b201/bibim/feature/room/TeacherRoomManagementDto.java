package com.b201.bibim.feature.room;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TeacherRoomManagementDto {

    private TeacherRoomManagementDto() {
    }

    public record CreateRoomRequest(
            @NotBlank(message = "방 이름을 입력해 주세요.")
            @Size(max = 100, message = "방 이름은 100자 이하여야 합니다.")
            String name,
            @NotNull(message = "학생 인원을 입력해 주세요.")
            @Min(value = 1, message = "학생 인원은 1명 이상이어야 합니다.")
            @Max(value = 50, message = "학생 인원은 50명 이하여야 합니다.")
            Integer expectedStudentCount
    ) {
    }

    public record RoomPreparationResponse(
            UUID roomId,
            String name,
            int expectedStudentCount,
            long connectedStudentCount,
            String status,
            String currentStage,
            List<StudentPreparationResponse> students
    ) {
    }

    public record StudentPreparationResponse(
            UUID studentId,
            int participantNumber,
            String participationCode,
            String nickname,
            String status,
            boolean online,
            Instant lastSeenAt
    ) {
    }
}
