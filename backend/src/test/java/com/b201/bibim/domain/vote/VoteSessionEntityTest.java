package com.b201.bibim.domain.vote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class VoteSessionEntityTest {

    @Test
    void followsReadyOpenClosedDecidedLifecycle() {
        VoteSessionEntity session = VoteSessionEntity.ready(UUID.randomUUID(), "어디에 사용할까요?", 120);
        UUID optionId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();

        assertEquals("READY", session.getStatus());
        session.open();
        assertEquals("OPEN", session.getStatus());
        assertNotNull(session.getOpenedAt());
        session.close();
        assertEquals("CLOSED", session.getStatus());
        session.decide(optionId, teacherId);

        assertEquals("DECIDED", session.getStatus());
        assertEquals(optionId, session.getDecidedOptionId());
        assertEquals(teacherId, session.getDecidedBy());
    }

    @Test
    void rejectsInvalidLifecycleTransitions() {
        VoteSessionEntity session = VoteSessionEntity.ready(UUID.randomUUID(), "어디에 사용할까요?", 120);

        assertThrows(IllegalStateException.class, session::close);
        session.open();
        assertThrows(IllegalStateException.class, session::open);
    }
}

