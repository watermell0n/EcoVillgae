package com.b201.bibim.feature.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.b201.bibim.feature.settlement.Day1IncomeCalculator.Calculation;

import org.junit.jupiter.api.Test;

class Day1IncomeCalculatorTest {

    private final Day1IncomeCalculator calculator = new Day1IncomeCalculator();

    @Test
    void calculatesQuizRewardsAndIncomeTax() {
        Calculation result = calculator.calculate(150, 0, 5, 1_000, true, 3);

        assertEquals(150, result.roleIncome());
        assertEquals(0, result.participationReward());
        assertEquals(15, result.correctReward());
        assertEquals(165, result.grossIncome());
        assertEquals(16, result.incomeTax());
        assertEquals(149, result.netIncome());
    }

    @Test
    void floorsIncomeTaxToWholeBibim() {
        Calculation result = calculator.calculate(150, 0, 5, 1_000, true, 5);

        assertEquals(175, result.grossIncome());
        assertEquals(17, result.incomeTax());
        assertEquals(158, result.netIncome());
    }

    @Test
    void excludesQuizRewardsWhenQuizWasNotSubmitted() {
        Calculation result = calculator.calculate(150, 0, 5, 1_000, false, 4);

        assertEquals(0, result.participationReward());
        assertEquals(0, result.correctReward());
        assertEquals(150, result.grossIncome());
        assertEquals(15, result.incomeTax());
    }
}
