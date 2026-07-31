package com.b201.ecovillage.ledger.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TransactionGroupTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-07-30T00:00:00Z");

    @Test
    void commitsOnlyThroughValidationAndBecomesTerminal() {
        UUID groupId = UUID.randomUUID();
        TransactionGroup group = TransactionGroup.receive(
            groupId,
            UUID.randomUUID(),
            "SALARY",
            LedgerActorType.TEACHER,
            UUID.randomUUID(),
            null,
            "request-1",
            "correlation-1",
            "급여 지급",
            RECEIVED_AT
        );

        group.startValidation(RECEIVED_AT.plusSeconds(1));
        group.commit(RECEIVED_AT.plusSeconds(2));

        assertEquals(TransactionGroupStatus.COMMITTED, group.getStatus());
        assertEquals(groupId, group.getRootTransactionGroupId());
        assertThrows(
            IllegalStateException.class,
            () -> group.reject("수정 금지", RECEIVED_AT.plusSeconds(3))
        );
    }

    @Test
    void rejectsInvalidRecoveryLinks() {
        UUID recoveryId = UUID.randomUUID();

        assertThrows(
            IllegalArgumentException.class,
            () -> TransactionGroup.receiveRecovery(
                recoveryId,
                UUID.randomUUID(),
                "RECOVERY",
                LedgerActorType.TEACHER,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                recoveryId,
                "request-2",
                "correlation-2",
                "복구",
                RECEIVED_AT
            )
        );
    }
}
