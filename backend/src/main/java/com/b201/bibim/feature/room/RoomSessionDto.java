package com.b201.bibim.feature.room;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class RoomSessionDto {

    private RoomSessionDto() {
    }

    public record ModuleStateResponse(
            String type,
            String state
    ) {
    }

    public record TeacherRoomStateResponse(
            UUID roomId,
            String roomName,
            String roomStatus,
            int currentDay,
            String currentStage,
            String mode,
            int version,
            long syncVersion,
            int expectedStudentCount,
            long connectedStudentCount,
            List<ModuleStateResponse> modules,
            boolean changed,
            Instant updatedAt
    ) {
    }

    public record StudentRoomStateResponse(
            UUID roomId,
            String roomStatus,
            int currentDay,
            String currentStage,
            String mode,
            long walletBalance,
            List<ModuleStateResponse> modules,
            long syncVersion,
            boolean changed
    ) {
    }

    public record CommandRequest(
            @NotBlank(message = "명령을 입력해 주세요.")
            @Size(max = 40, message = "명령이 너무 깁니다.")
            String command,
            @NotBlank(message = "이동할 단계를 입력해 주세요.")
            @Size(max = 50, message = "단계명이 너무 깁니다.")
            String targetStage,
            @NotNull(message = "현재 방 버전이 필요합니다.")
            @Min(value = 0, message = "방 버전은 0 이상이어야 합니다.")
            Integer expectedVersion
    ) {
    }

    public record CommandResponse(
            TeacherRoomStateResponse state,
            boolean replayed
    ) {
    }
}
