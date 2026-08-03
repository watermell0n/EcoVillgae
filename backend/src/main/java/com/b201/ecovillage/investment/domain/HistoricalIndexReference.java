package com.b201.ecovillage.investment.domain;

import java.time.LocalDate;

public record HistoricalIndexReference(
    String seriesCode,
    String eventName,
    String sourceName,
    String sourceUrl,
    LocalDate periodStart,
    LocalDate periodEnd
) {
    public HistoricalIndexReference {
        if (
            seriesCode == null || !seriesCode.matches("^[A-Z][A-Z0-9_.-]{1,39}$")
                || eventName == null || eventName.isBlank() || eventName.length() > 120
                || sourceName == null || sourceName.isBlank() || sourceName.length() > 120
                || sourceUrl == null || sourceUrl.isBlank() || sourceUrl.length() > 500
                || periodStart == null || periodEnd == null || !periodStart.isBefore(periodEnd)
        ) {
            throw new IllegalArgumentException("A valid historical index reference is required");
        }
    }
}
