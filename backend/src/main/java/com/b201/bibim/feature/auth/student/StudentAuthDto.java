package com.b201.bibim.feature.auth.student;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class StudentAuthDto {

    private StudentAuthDto() {
    }

    public record EnterRequest(
            @NotBlank(message = "참여 코드를 입력해 주세요.")
            @Size(max = 20, message = "참여 코드가 너무 깁니다.")
            String participationCode
    ) {
    }

    public record SetupRequest(
            @NotBlank(message = "참여 코드를 입력해 주세요.")
            @Size(max = 20, message = "참여 코드가 너무 깁니다.")
            String participationCode,
            @NotBlank(message = "닉네임을 입력해 주세요.")
            @Size(min = 2, max = 20, message = "닉네임은 2~20자로 입력해 주세요.")
            String nickname,
            @NotBlank(message = "비밀번호를 입력해 주세요.")
            @Size(min = 4, max = 30, message = "비밀번호는 4~30자로 입력해 주세요.")
            String password
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "참여 코드를 입력해 주세요.")
            @Size(max = 20, message = "참여 코드가 너무 깁니다.")
            String participationCode,
            @NotBlank(message = "비밀번호를 입력해 주세요.")
            @Size(max = 30, message = "비밀번호가 너무 깁니다.")
            String password
    ) {
    }

    public enum RequiredAction {
        SETUP,
        LOGIN,
        WAIT,
        ROOM_CLOSED
    }

    public record EnterResponse(
            UUID roomId,
            String roomName,
            int participantNumber,
            RequiredAction requiredAction
    ) {
    }

    public record StudentResponse(
            UUID studentId,
            UUID roomId,
            String roomName,
            int participantNumber,
            String nickname,
            String roomStatus,
            int currentDay,
            String currentStage,
            String mode,
            long walletBalance,
            long syncVersion,
            String connectionStatus
    ) {
    }
}
