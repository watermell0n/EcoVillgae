package com.b201.ecovillage.ledger.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LedgerAccountTest {

    private static final Instant OPENED_AT = Instant.parse("2026-07-30T00:00:00Z");

    @Test
    void postsSequentialBalanceSnapshots() {
        LedgerAccount wallet = LedgerAccount.studentWallet(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            OPENED_AT
        );

        LedgerAccountPosting credit = wallet.post(100, OPENED_AT.plusSeconds(1));
        LedgerAccountPosting debit = wallet.post(-40, OPENED_AT.plusSeconds(2));

        assertEquals(new LedgerAccountPosting(1, 100, 0, 100), credit);
        assertEquals(new LedgerAccountPosting(2, -40, 100, 60), debit);
        assertEquals(60, wallet.getCurrentBalance());
        assertEquals(3, wallet.getNextEntrySequence());
    }

    @Test
    void rejectsInsufficientStudentBalanceWithoutChangingAccount() {
        LedgerAccount wallet = LedgerAccount.studentWallet(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            OPENED_AT
        );
        wallet.post(30, OPENED_AT.plusSeconds(1));

        assertThrows(
            InsufficientLedgerBalanceException.class,
            () -> wallet.post(-31, OPENED_AT.plusSeconds(2))
        );
        assertEquals(30, wallet.getCurrentBalance());
        assertEquals(2, wallet.getNextEntrySequence());
    }

    @Test
    void preventsStudentAndSystemOwnershipFromBeingMixed() {
        assertThrows(
            IllegalArgumentException.class,
            () -> LedgerAccount.systemAccount(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "student-wallet",
                LedgerAccountType.STUDENT_WALLET,
                OPENED_AT
            )
        );
    }
}
