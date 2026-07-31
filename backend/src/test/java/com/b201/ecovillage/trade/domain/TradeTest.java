package com.b201.ecovillage.trade.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TradeTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    @Test
    void counterpartAcceptanceMovesProposalToTeacherQueue() {
        UUID counterpartId = UUID.randomUUID();
        Trade trade = proposal(UUID.randomUUID(), counterpartId);

        trade.acceptByCounterpart(counterpartId, NOW.plusSeconds(10));

        assertEquals(TradeStatus.AWAITING_TEACHER, trade.getStatus());
    }

    @Test
    void proposerCanCancelBeforeTeacherDecision() {
        UUID proposerId = UUID.randomUUID();
        Trade trade = proposal(proposerId, UUID.randomUUID());

        trade.cancelByProposer(proposerId, NOW.plusSeconds(10));

        assertEquals(TradeStatus.CANCELED, trade.getStatus());
    }

    @Test
    void wrongStudentCannotDecideAndTerminalTradeCannotChangeAgain() {
        UUID proposerId = UUID.randomUUID();
        UUID counterpartId = UUID.randomUUID();
        Trade trade = proposal(proposerId, counterpartId);

        assertThrows(
            TradeOperationException.class,
            () -> trade.acceptByCounterpart(UUID.randomUUID(), NOW.plusSeconds(5))
        );
        trade.rejectByCounterpart(counterpartId, NOW.plusSeconds(10));
        assertThrows(
            TradeOperationException.class,
            () -> trade.cancelByProposer(proposerId, NOW.plusSeconds(20))
        );
    }

    @Test
    void rejectsSelfTrade() {
        UUID studentId = UUID.randomUUID();

        TradeOperationException exception = assertThrows(
            TradeOperationException.class,
            () -> proposal(studentId, studentId)
        );

        assertEquals("SELF_TRADE_FORBIDDEN", exception.getCode());
    }

    private static Trade proposal(UUID proposerId, UUID counterpartId) {
        return Trade.propose(
            UUID.randomUUID(),
            UUID.randomUUID(),
            proposerId,
            counterpartId,
            "item exchange",
            NOW.plusSeconds(3600),
            NOW
        );
    }
}
