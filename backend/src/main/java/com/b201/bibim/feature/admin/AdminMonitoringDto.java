package com.b201.bibim.feature.admin;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AdminMonitoringDto {

    private AdminMonitoringDto() {
    }

    public record RoomMonitoringResponse(
            UUID roomId,
            String roomName,
            String roomStatus,
            int currentDay,
            String currentStage,
            int version,
            long syncVersion,
            int expectedStudentCount,
            int onlineCount,
            int unstableCount,
            int awayCount,
            int offlineCount,
            int neverConnectedCount,
            Instant updatedAt
    ) {
    }

    public record StudentMonitoringResponse(
            UUID studentId,
            int participantNumber,
            String participationCode,
            String nickname,
            String studentStatus,
            String connectionStatus,
            String currentPage,
            String currentModule,
            String reportedStage,
            String roomStage,
            long clientSyncVersion,
            long roomSyncVersion,
            String syncStatus,
            String flowMode,
            String effectiveStage,
            int completedActivityCount,
            int totalActivityCount,
            int rewardRateBps,
            long walletBalance,
            Instant firstSeenAt,
            Instant lastSeenAt,
            Instant reconnectedAt
    ) {
    }

    public record RoomStudentsResponse(
            UUID roomId,
            String roomName,
            String currentStage,
            long syncVersion,
            List<StudentMonitoringResponse> students
    ) {
    }

    public record ActivityResponse(
            UUID eventId,
            String actorType,
            String eventType,
            String stage,
            String module,
            String page,
            String requestId,
            Map<String, Object> metadata,
            Instant occurredAt
    ) {
    }

    public record FinanceReportDeposit(
            UUID depositId,
            long principal,
            long expectedInterest,
            Instant confirmedAt
    ) {
    }

    public record FinanceReportInvestmentAllocation(
            UUID assetId,
            String assetName,
            long amount
    ) {
    }

    public record FinanceReportInvestment(
            UUID orderId,
            long totalAmount,
            List<FinanceReportInvestmentAllocation> allocations,
            Instant confirmedAt
    ) {
    }

    public record FinanceReportPurchase(
            UUID purchaseId,
            UUID productId,
            String productName,
            int quantity,
            long unitPrice,
            long totalPrice,
            Instant purchasedAt
    ) {
    }

    public record FinanceReportTransaction(
            UUID transactionId,
            String transactionType,
            String description,
            long walletAmount,
            Long walletBalanceAfter,
            Map<String, Object> metadata,
            Instant occurredAt
    ) {
    }

    public record StudentFinanceReport(
            UUID studentId,
            int participantNumber,
            String nickname,
            long walletBalance,
            long depositBalance,
            long investmentBalance,
            List<FinanceReportDeposit> deposits,
            List<FinanceReportInvestment> investments,
            List<FinanceReportPurchase> purchases,
            List<FinanceReportTransaction> transactions
    ) {
    }

    public record RoomFinanceReportResponse(
            UUID roomId,
            String roomName,
            Instant generatedAt,
            List<StudentFinanceReport> students
    ) {
    }

    public record SessionTerminationRequest(
            @NotBlank(message = "세션 종료 사유를 입력해 주세요.")
            @Size(max = 200, message = "세션 종료 사유는 200자 이하여야 합니다.")
            String reason
    ) {
    }

    public record SessionTerminationResponse(
            UUID studentId,
            int revokedSessionCount,
            Instant terminatedAt
    ) {
    }

    public record WalletAdjustmentRequest(
            @Min(value = -1000000, message = "한 번에 조정할 수 있는 잔액은 -1,000,000 이상이어야 합니다.")
            @Max(value = 1000000, message = "한 번에 조정할 수 있는 잔액은 1,000,000 이하여야 합니다.")
            long amount,
            @NotBlank(message = "잔액 조정 사유를 입력해 주세요.")
            @Size(max = 200, message = "잔액 조정 사유는 200자 이하여야 합니다.")
            String reason
    ) {
    }

    public record WalletAdjustmentResponse(
            UUID studentId,
            long balance,
            long adjustedAmount,
            Instant adjustedAt
    ) {
    }

    public record ForceStudentViewRequest(
            @NotNull(message = "이동할 화면을 선택해 주세요.")
            String targetStage
    ) {
    }
}
