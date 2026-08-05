package com.b201.ecovillage.finance.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FinancialProductVersionTest {

    @Test
    void savingsUsesOneInstallmentPerWeek() {
        assertThatNoException().isThrownBy(() -> savingsVersion(28, 4));
        assertThatThrownBy(() -> savingsVersion(21, 4))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("weekly");
    }

    private static FinancialProductVersion savingsVersion(int termDays, int installmentCount) {
        return FinancialProductVersion.issue(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            FinancialProductType.SAVINGS,
            termDays,
            1_500,
            200,
            installmentCount,
            20L,
            20,
            80,
            false,
            Instant.parse("2026-08-05T00:00:00Z")
        );
    }
}
