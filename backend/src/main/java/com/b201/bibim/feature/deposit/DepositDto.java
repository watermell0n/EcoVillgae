package com.b201.bibim.feature.deposit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Positive;

public final class DepositDto {

    private DepositDto() {
    }

    public record SaveDraftRequest(
            @Positive(message = "예금액은 0보다 커야 합니다.")
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
            long minimumAmount,
            long amountUnit,
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
