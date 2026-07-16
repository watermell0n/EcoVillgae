package com.b201.bibim.feature.monitoring;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class StudentPresenceDto {

    private StudentPresenceDto() {
    }

    public record HeartbeatRequest(
            @NotBlank(message = "현재 단계를 입력해 주세요.")
            @Size(max = 50, message = "단계명이 너무 깁니다.")
            String reportedStage,
            @Size(max = 40, message = "모듈명이 너무 깁니다.")
            String currentModule,
            @NotBlank(message = "현재 페이지를 입력해 주세요.")
            @Size(max = 80, message = "페이지명이 너무 깁니다.")
            String currentPage,
            @NotNull(message = "동기화 버전이 필요합니다.")
            @Min(value = 0, message = "동기화 버전은 0 이상이어야 합니다.")
            Long clientSyncVersion,
            @NotBlank(message = "화면 상태를 입력해 주세요.")
            @Pattern(regexp = "VISIBLE|HIDDEN", message = "화면 상태가 올바르지 않습니다.")
            String visibility
    ) {
    }

    public record HeartbeatResponse(
            UUID studentId,
            String connectionStatus,
            String serverStage,
            long serverSyncVersion,
            boolean synchronizedWithRoom,
            Instant receivedAt
    ) {
    }
}
