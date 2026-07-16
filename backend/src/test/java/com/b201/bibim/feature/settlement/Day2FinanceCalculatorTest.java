package com.b201.bibim.feature.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import com.b201.bibim.feature.settlement.Day2FinanceCalculator.AllocationInput;
import com.b201.bibim.feature.settlement.Day2FinanceCalculator.Calculation;

import org.junit.jupiter.api.Test;

class Day2FinanceCalculatorTest {

    private final Day2FinanceCalculator calculator = new Day2FinanceCalculator();

    @Test
    void calculatesDepositInterestInvestmentNetProfitAndFinancialTax() {
        Calculation result = calculator.calculate(
                25,
                20,
                500,
                List.of(
                        new AllocationInput(UUID.randomUUID(), 10, 200),
                        new AllocationInput(UUID.randomUUID(), 10, 700)
                ),
                1000
        );

        assertEquals(1, result.depositInterest());
        assertEquals(1, result.investmentProfit());
        assertEquals(2, result.taxableProfit());
        assertEquals(0, result.financialTax());
        assertEquals(67, result.walletAfter());
    }

    @Test
    void taxesDepositInterestButDoesNotTaxNegativeInvestmentProfit() {
        Calculation result = calculator.calculate(
                30,
                100,
                1000,
                List.of(new AllocationInput(UUID.randomUUID(), 100, -600)),
                1000
        );

        assertEquals(10, result.depositInterest());
        assertEquals(-6, result.investmentProfit());
        assertEquals(10, result.taxableProfit());
        assertEquals(1, result.financialTax());
        assertEquals(233, result.walletAfter());
    }

    @Test
    void returnsNormalZeroResultForStudentWithoutFinanceChoices() {
        Calculation result = calculator.calculate(90, 0, 0, List.of(), 1000);

        assertEquals(0, result.depositInterest());
        assertEquals(0, result.investmentValue());
        assertEquals(0, result.financialTax());
        assertEquals(90, result.walletAfter());
    }
}
