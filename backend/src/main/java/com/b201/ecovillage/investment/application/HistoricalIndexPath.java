package com.b201.ecovillage.investment.application;

import java.util.List;

import com.b201.ecovillage.investment.domain.HistoricalIndexReference;

public record HistoricalIndexPath(
    HistoricalIndexReference reference,
    List<Integer> changeRatesBps
) {
    public HistoricalIndexPath {
        changeRatesBps = List.copyOf(changeRatesBps);
    }
}
