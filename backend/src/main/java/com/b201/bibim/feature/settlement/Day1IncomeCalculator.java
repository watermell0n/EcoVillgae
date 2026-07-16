package com.b201.bibim.feature.settlement;

import org.springframework.stereotype.Component;

@Component
public class Day1IncomeCalculator {

    private static final long BASIS_POINTS_DIVISOR = 10_000L;

    public Calculation calculate(
            long roleIncome,
            long participationRewardUnit,
            long correctRewardUnit,
            int incomeTaxBps,
            boolean quizSubmitted,
            int correctCount
    ) {
        if (roleIncome < 0
                || participationRewardUnit < 0
                || correctRewardUnit < 0
                || incomeTaxBps < 0
                || correctCount < 0) {
            throw new IllegalArgumentException("소득 정산 값은 음수일 수 없습니다.");
        }

        long participationReward = quizSubmitted ? participationRewardUnit : 0;
        long correctReward = quizSubmitted
                ? Math.multiplyExact(correctRewardUnit, correctCount)
                : 0;
        long grossIncome = Math.addExact(
                Math.addExact(roleIncome, participationReward),
                correctReward
        );
        long incomeTax = Math.floorDiv(
                Math.multiplyExact(grossIncome, incomeTaxBps),
                BASIS_POINTS_DIVISOR
        );

        return new Calculation(
                roleIncome,
                participationReward,
                correctReward,
                grossIncome,
                incomeTax,
                grossIncome - incomeTax
        );
    }

    public record Calculation(
            long roleIncome,
            long participationReward,
            long correctReward,
            long grossIncome,
            long incomeTax,
            long netIncome
    ) {
    }
}
