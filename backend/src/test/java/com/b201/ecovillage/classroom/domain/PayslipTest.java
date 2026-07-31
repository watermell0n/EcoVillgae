package com.b201.ecovillage.classroom.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PayslipTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    @Test
    void derivesNetAmountFromGrossDeductionAndTax() {
        Payslip payslip = Payslip.issue(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "SESSION-03",
            1_000,
            100,
            45,
            UUID.randomUUID(),
            UUID.randomUUID(),
            NOW
        );

        assertEquals(855, payslip.getNetAmount());
    }

    @Test
    void rejectsWithholdingThatExceedsGrossAmount() {
        assertThrows(
            IllegalArgumentException.class,
            () -> Payslip.issue(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SESSION-03",
                100,
                80,
                30,
                UUID.randomUUID(),
                UUID.randomUUID(),
                NOW
            )
        );
    }
}
