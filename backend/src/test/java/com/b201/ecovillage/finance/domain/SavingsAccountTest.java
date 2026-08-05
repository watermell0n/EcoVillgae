package com.b201.ecovillage.finance.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SavingsAccountTest {
    @Test
    void paysEachInstallmentAndPaysInterestOnlyWhenFullyPaid() {
        Instant openedAt = Instant.parse("2026-08-05T00:00:00Z");
        SavingsAccount account = SavingsAccount.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 20, 3, 9, openedAt, openedAt.plusSeconds(86_400), UUID.randomUUID());

        assertFalse(account.isFullyPaid());
        assertEquals(20, account.maturityAmount());
        account.recordInstallment(openedAt.plusSeconds(1));
        assertEquals(40, account.getPrincipalBalance());
        account.recordInstallment(openedAt.plusSeconds(2));
        assertTrue(account.isFullyPaid());
        assertEquals(69, account.maturityAmount());

        account.mature(UUID.randomUUID(), openedAt.plusSeconds(86_400));
        assertEquals(SavingsAccountStatus.MATURED, account.getStatus());
    }
}
