package com.b201.ecovillage.finance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class LoanRepaymentPlanTest {

    @Test
    void createsFourWeeklyPaymentsAndKeepsIntegerRemaindersInTheLastPayment() {
        Instant startedAt = Instant.parse("2026-08-05T00:00:00Z");

        LoanRepaymentPlan plan = LoanRepaymentPlan.create(101, 700, 28, 4, startedAt);

        assertThat(plan.totalInterest()).isEqualTo(7);
        assertThat(plan.maturesAt()).isEqualTo(startedAt.plusSeconds(28L * 86_400));
        assertThat(plan.installments()).extracting(LoanRepaymentPlan.Installment::principal)
            .containsExactly(25L, 25L, 25L, 26L);
        assertThat(plan.installments()).extracting(LoanRepaymentPlan.Installment::interest)
            .containsExactly(1L, 1L, 1L, 4L);
        assertThat(plan.installments()).extracting(LoanRepaymentPlan.Installment::dueAt)
            .containsExactly(
                startedAt.plusSeconds(7L * 86_400),
                startedAt.plusSeconds(14L * 86_400),
                startedAt.plusSeconds(21L * 86_400),
                startedAt.plusSeconds(28L * 86_400)
            );
    }
}
