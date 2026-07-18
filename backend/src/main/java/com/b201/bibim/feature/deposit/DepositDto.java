package com.b201.bibim.feature.deposit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;

public final class DepositDto {

    private DepositDto() {
    }

    public record SaveDraftRequest(
            @Min(value = 20, message = "예금은 20비빔 이상이어야 합니다.")
            long principal
    ) {
    }

    public record DepositResponse(
            UUID depositId,
            String status,
            long principal,
            int interestRateBps,
            long expectedInterest,
            long expectedMaturityAmount,
            long walletBalance,
            Instant updatedAt,
            Instant confirmedAt,
            boolean replayed
    ) {
    }

    public record DepositHistoryItemResponse(
            UUID depositId,
            long principal,
            int interestRateBps,
            long expectedInterest,
            long expectedMaturityAmount,
            Instant confirmedAt
    ) {
    }

    public record DepositHistoryResponse(
            List<DepositHistoryItemResponse> deposits
    ) {
    }
}
