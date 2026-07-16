package com.b201.bibim.feature.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.b201.bibim.feature.settlement.Day1IncomeCalculator.Calculation;

import org.junit.jupiter.api.Test;

class Day1IncomeCalculatorTest {

    private final Day1IncomeCalculator calculator = new Day1IncomeCalculator();

    @Test
    void calculatesQuizRewardsAndIncomeTax() {
        Calculation result = calculator.calculate(100, 10, 10, 1_000, true, 3);

        assertEquals(100, result.roleIncome());
        assertEquals(10, result.participationReward());
        assertEquals(30, result.correctReward());
        assertEquals(140, result.grossIncome());
        assertEquals(14, result.incomeTax());
        assertEquals(126, result.netIncome());
    }

    @Test
    void floorsIncomeTaxToWholeBibim() {
        Calculation result = calculator.calculate(100, 10, 5, 1_000, true, 5);

        assertEquals(135, result.grossIncome());
        assertEquals(13, result.incomeTax());
        assertEquals(122, result.netIncome());
    }

    @Test
    void excludesQuizRewardsWhenQuizWasNotSubmitted() {
        Calculation result = calculator.calculate(100, 10, 10, 1_000, false, 4);

        assertEquals(0, result.participationReward());
        assertEquals(0, result.correctReward());
        assertEquals(100, result.grossIncome());
        assertEquals(10, result.incomeTax());
    }
}
