package com.b201.bibim.feature.flow;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class StudentFlowDto {

    private StudentFlowDto() {
    }

    public record FlowResponse(
            UUID studentId,
            UUID roomId,
            String roomStage,
            String effectiveStage,
            String flowMode,
            String joinStatus,
            String attemptType,
            int rewardRateBps,
            int completedCount,
            int totalCount,
            boolean canAdvance
    ) {
    }

    public record TeacherStudentFlowResponse(
            UUID studentId,
            int participantNumber,
            String nickname,
            String studentStatus,
            boolean online,
            FlowResponse flow
    ) {
    }

    public record OverrideRequest(
            @NotBlank(message = "처리 사유를 입력해 주세요.")
            @Size(max = 200, message = "처리 사유는 200자 이하여야 합니다.")
            String reason
    ) {
    }

    public record TeacherFlowListResponse(
            UUID roomId,
            String roomStage,
            List<TeacherStudentFlowResponse> students
    ) {
    }
}
