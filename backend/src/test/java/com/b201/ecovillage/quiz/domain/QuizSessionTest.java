package com.b201.ecovillage.quiz.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class QuizSessionTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    @Test
    void closesOpenSessionForScoringOnlyOnce() {
        QuizSession session = QuizSession.open(
            UUID.randomUUID(),
            UUID.randomUUID(),
            QuizMode.GAME,
            QuizContentType.ECONOMY,
            Map.of("base_reward", 100),
            true,
            NOW
        );

        session.closeSubmissions(NOW.plusSeconds(30));

        assertEquals(QuizSessionStatus.SCORING, session.getStatus());
        assertFalse(session.acceptsAnswers());
        assertThrows(
            IllegalStateException.class,
            () -> session.closeSubmissions(NOW.plusSeconds(40))
        );
    }
}
