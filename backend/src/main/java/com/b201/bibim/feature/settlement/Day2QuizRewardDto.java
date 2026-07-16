package com.b201.bibim.feature.settlement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Day2QuizRewardDto {

    private Day2QuizRewardDto() {
    }

    public record StudentRewardResponse(
            UUID studentId,
            int participantNumber,
            String nickname,
            boolean submitted,
            int correctCount,
            long participationReward,
            long correctReward,
            long totalReward,
            long walletAfter
    ) {
    }

    public record TeacherRewardPreviewResponse(
            UUID roomId,
            String settlementStatus,
            long participationRewardPerStudent,
            long correctRewardPerQuestion,
            int targetCount,
            int submittedCount,
            int notSubmittedCount,
            long totalReward,
            Instant completedAt,
            List<StudentRewardResponse> students
    ) {
    }

    public record SettlementResponse(
            TeacherRewardPreviewResponse settlement,
            boolean replayed
    ) {
    }
}
