package com.b201.bibim.feature.auth.student;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class StudentAuthDto {

    private StudentAuthDto() {
    }

    public record EnterRequest(
            @Size(max = 20, message = "참여 코드가 너무 깁니다.")
            String participationCode,
            UUID roomId,
            @Min(value = 1, message = "학생 번호는 1번부터 입력할 수 있어요.")
            @Max(value = 99, message = "학생 번호는 99번까지 입력할 수 있어요.")
            Integer participantNumber
    ) {
        public EnterRequest(String participationCode) {
            this(participationCode, null, null);
        }
    }

    public record SetupRequest(
            @NotBlank(message = "참여 코드를 입력해 주세요.")
            @Size(max = 20, message = "참여 코드가 너무 깁니다.")
            String participationCode,
            @Size(min = 1, max = 20, message = "이름은 앞뒤 공백을 빼고 1~20글자로 입력해 주세요.")
            String nickname,
            @NotBlank(message = "비밀번호를 입력해 주세요.")
            @Size(min = 4, max = 30, message = "비밀번호는 4~30자로 입력해 주세요.")
            @Pattern(regexp = "\\S+", message = "비밀번호에는 공백을 사용할 수 없습니다.")
            String password
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "참여 코드를 입력해 주세요.")
            @Size(max = 20, message = "참여 코드가 너무 깁니다.")
            String participationCode,
            @NotBlank(message = "비밀번호를 입력해 주세요.")
            @Size(max = 30, message = "비밀번호가 너무 깁니다.")
            @Pattern(regexp = "\\S+", message = "비밀번호에는 공백을 사용할 수 없습니다.")
            String password
    ) {
    }

    public enum RequiredAction {
        SETUP,
        RESET_PASSWORD,
        LOGIN,
        WAIT,
        ROOM_CLOSED
    }

    public record EnterResponse(
            String participationCode,
            UUID roomId,
            String roomName,
            int participantNumber,
            String nickname,
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
