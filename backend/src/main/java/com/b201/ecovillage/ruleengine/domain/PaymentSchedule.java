package com.b201.ecovillage.ruleengine.domain;

import java.time.LocalDate;

public record PaymentSchedule(
    int operationDays,
    int payIntervalDays,
    LocalDate firstPayOn,
    int paymentCount
) {
}
