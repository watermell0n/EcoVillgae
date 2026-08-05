package com.b201.ecovillage.finance.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record LoanRepaymentPlan(
    long principal,
    long totalInterest,
    Instant maturesAt,
    List<Installment> installments
) {

    public LoanRepaymentPlan {
        installments = List.copyOf(installments);
    }

    public static LoanRepaymentPlan create(
        long principal,
        int interestRateBps,
        int termDays,
        int installmentCount,
        Instant startedAt
    ) {
        if (principal < installmentCount || interestRateBps < 0 || interestRateBps > 10_000
            || termDays < installmentCount || installmentCount < 1 || startedAt == null) {
            throw new IllegalArgumentException("loan repayment values are invalid");
        }
        long interest = BigDecimal.valueOf(principal)
            .multiply(BigDecimal.valueOf(interestRateBps))
            .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.DOWN)
            .longValueExact();
        long principalBase = principal / installmentCount;
        long principalRemainder = principal % installmentCount;
        long interestBase = interest / installmentCount;
        long interestRemainder = interest % installmentCount;
        List<Installment> schedule = new ArrayList<>(installmentCount);
        for (int number = 1; number <= installmentCount; number++) {
            boolean last = number == installmentCount;
            long dueAfterDays = Math.max(1L, (long) termDays * number / installmentCount);
            schedule.add(new Installment(
                number,
                principalBase + (last ? principalRemainder : 0),
                interestBase + (last ? interestRemainder : 0),
                startedAt.plus(Duration.ofDays(dueAfterDays))
            ));
        }
        return new LoanRepaymentPlan(
            principal,
            interest,
            startedAt.plus(Duration.ofDays(termDays)),
            schedule
        );
    }

    public record Installment(
        int number,
        long principal,
        long interest,
        Instant dueAt
    ) {
    }
}
