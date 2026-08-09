package com.b201.ecovillage.vote.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class VoteTest {

    private static final Instant CREATED_AT =
        Instant.parse("2026-07-30T00:00:00Z");

    @Test
    void followsTheReadyOpenClosedPublishedLifecycle() {
        Vote vote = Vote.ready(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "공동기금 사용처",
            "학급 공동기금 사용처를 고릅니다.",
            CREATED_AT
        );

        vote.open(CREATED_AT.plusSeconds(1));
        vote.close(CREATED_AT.plusSeconds(2));
        vote.publishResult(CREATED_AT.plusSeconds(3));

        assertTrue(vote.isAnonymous());
        assertEquals(VoteStatus.RESULT_PUBLISHED, vote.getStatus());
        assertEquals(CREATED_AT.plusSeconds(1), vote.getOpensAt());
        assertEquals(CREATED_AT.plusSeconds(2), vote.getClosesAt());
    }

    @Test
    void rejectsSkippingAState() {
        Vote vote = Vote.ready(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "공동기금 사용처",
            null,
            CREATED_AT
        );

        assertThrows(
            IllegalStateException.class,
            () -> vote.close(CREATED_AT.plusSeconds(1))
        );
    }

    @Test
    void reopensAClosedVoteBeforePublishingTheResult() {
        Vote vote = Vote.ready(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "공동기금 사용처",
            null,
            CREATED_AT
        );

        vote.open(CREATED_AT.plusSeconds(1));
        vote.close(CREATED_AT.plusSeconds(2));
        vote.reopen(CREATED_AT.plusSeconds(3));

        assertEquals(VoteStatus.OPEN, vote.getStatus());
        assertEquals(CREATED_AT.plusSeconds(3), vote.getOpensAt());
        assertNull(vote.getClosesAt());
    }

    @Test
    void eligibilityCanBeConsumedOnlyOnce() {
        VoteEligibility eligibility = VoteEligibility.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "eligibility-token-hash",
            CREATED_AT
        );

        eligibility.use(CREATED_AT.plusSeconds(1));

        assertThrows(
            IllegalStateException.class,
            () -> eligibility.use(CREATED_AT.plusSeconds(2))
        );
    }
}
