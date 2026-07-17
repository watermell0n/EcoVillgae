package com.b201.bibim.feature.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class InflationCalculatorTest {

    private final InflationCalculator calculator = new InflationCalculator();

    @Test
    void increasesPriceByTenPercent() {
        assertEquals(110, calculator.calculate(100, 1000));
    }

    @Test
    void roundsHalfUpToAnInteger() {
        assertEquals(28, calculator.calculate(25, 1000));
        assertEquals(44, calculator.calculate(40, 1000));
    }

    @Test
    void rejectsNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(-1, 1000));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(100, -1));
    }
}
