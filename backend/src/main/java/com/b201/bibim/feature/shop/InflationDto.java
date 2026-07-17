package com.b201.bibim.feature.shop;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InflationDto {

    private InflationDto() {
    }

    public record ProductPriceResponse(
            UUID productId,
            String name,
            long originalPrice,
            long priceAfterInflation,
            long increaseAmount,
            int currentStock
    ) {
    }

    public record PreviewResponse(
            UUID roomId,
            int inflationBps,
            boolean applied,
            Instant appliedAt,
            List<ProductPriceResponse> products
    ) {
    }

    public record ApplyResponse(
            PreviewResponse inflation,
            boolean replayed
    ) {
    }
}
