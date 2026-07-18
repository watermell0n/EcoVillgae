package com.b201.bibim.domain.student;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class StudentFlowStateEntityTest {

    @Test
    void approvedStudentSeesCurrentBoundaryStageImmediately() {
        StudentFlowStateEntity state = StudentFlowStateEntity.catchUp(
                UUID.randomUUID(), UUID.randomUUID()
        );
        state.awaitJoin();

        state.approve(UUID.randomUUID(), "DAY2_QUIZ_SETTLEMENT", true);

        assertEquals("AWAITING_JOIN", state.getFlowMode());
        assertEquals("APPROVED_PENDING_BOUNDARY", state.getJoinStatus());
        assertEquals("DAY2_QUIZ_SETTLEMENT", state.getEffectiveStage());
        assertEquals(8_000, state.getRewardRateBps());
    }

    @Test
    void approvedStudentJoinsImmediatelyAtSafeStage() {
        StudentFlowStateEntity state = StudentFlowStateEntity.catchUp(
                UUID.randomUUID(), UUID.randomUUID()
        );
        state.awaitJoin();

        state.approve(UUID.randomUUID(), "DAY1_QUIZ_RESULT", false);

        assertEquals("LIVE", state.getFlowMode());
        assertEquals("JOINED", state.getJoinStatus());
        assertEquals("DAY1_QUIZ_RESULT", state.getEffectiveStage());
        assertEquals(10_000, state.getRewardRateBps());
    }
}
