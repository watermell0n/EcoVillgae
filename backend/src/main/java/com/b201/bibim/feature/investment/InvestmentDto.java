package com.b201.bibim.feature.investment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class InvestmentDto {

    private InvestmentDto() {
    }

    public record AssetResponse(
            UUID assetId,
            String code,
            String name,
            String description,
            String companyInfo,
            String riskLevel,
            int minReturnBps,
            int maxReturnBps,
            int displayOrder
    ) {
    }

    public record AllocationRequest(
            @NotNull UUID assetId,
            @Positive long amount
    ) {
    }

    public record SaveDraftRequest(
            @NotEmpty(message = "투자처를 한 곳 이상 선택해 주세요.")
            @Size(max = 3, message = "투자처는 최대 세 곳까지 선택할 수 있습니다.")
            List<@Valid AllocationRequest> allocations
    ) {
    }

    public record AllocationResponse(
            UUID assetId,
            String assetName,
            String riskLevel,
            long amount
    ) {
    }

    public record InvestmentResponse(
            UUID orderId,
            String status,
            long totalAmount,
            long walletBalance,
            boolean scenarioLocked,
            List<AssetResponse> assets,
            List<AllocationResponse> allocations,
            Instant updatedAt,
            Instant confirmedAt,
            boolean replayed
    ) {
    }

    public record InvestmentHistoryItemResponse(
            UUID orderId,
            long totalAmount,
            List<AllocationResponse> allocations,
            Instant confirmedAt
    ) {
    }

    public record InvestmentHistoryResponse(
            List<InvestmentHistoryItemResponse> investments
    ) {
    }
}
