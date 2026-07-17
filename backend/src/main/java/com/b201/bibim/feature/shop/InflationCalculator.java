package com.b201.bibim.feature.shop;

import org.springframework.stereotype.Component;

@Component
public class InflationCalculator {

    public long calculate(long originalPrice, int inflationBps) {
        if (originalPrice < 0 || inflationBps < 0) {
            throw new IllegalArgumentException("가격과 물가상승률은 0 이상이어야 합니다.");
        }
        long multiplied = Math.multiplyExact(
                originalPrice,
                Math.addExact(10_000, inflationBps)
        );
        return Math.addExact(multiplied, 5_000) / 10_000;
    }
}
