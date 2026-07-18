package com.b201.bibim.domain.room;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoomStageTest {

    @Test
    void anyDifferentStageInTheSameDayCanBeSelected() {
        assertTrue(RoomStage.DAY1_LOBBY.canMoveTo(RoomStage.DAY1_PROFILE));
        assertTrue(RoomStage.DAY1_PROFILE.canMoveTo(RoomStage.DAY1_LOBBY));
        assertTrue(RoomStage.DAY1_LOBBY.canMoveTo(RoomStage.DAY1_QUIZ));
        assertFalse(RoomStage.DAY1_LOBBY.canMoveTo(RoomStage.DAY1_LOBBY));
        assertFalse(RoomStage.DAY1_LOBBY.canMoveTo(RoomStage.DAY2_LOGIN));
    }

    @Test
    void stageDeterminesDayRoomStatusAndMode() {
        assertTrue(RoomStage.DAY1_FREE_ACTIVITY.day() == 1);
        assertTrue(RoomStage.DAY1_FREE_ACTIVITY.roomStatus().equals("DAY1_ACTIVE"));
        assertTrue(RoomStage.DAY1_FREE_ACTIVITY.mode().equals("FREE"));
        assertTrue(RoomStage.DAY2_COMPLETED.day() == 2);
        assertTrue(RoomStage.DAY2_COMPLETED.roomStatus().equals("COMPLETED"));
        assertTrue(RoomStage.DAY2_COMPLETED.mode().equals("CLOSED"));
    }

    @Test
    void placesFundResultBetweenVoteAndFinalResult() {
        assertTrue(RoomStage.DAY2_FUND_VOTE.canMoveTo(RoomStage.DAY2_FUND_RESULT));
        assertTrue(RoomStage.DAY2_FUND_RESULT.canMoveTo(RoomStage.DAY2_FINAL_RESULT));
        assertTrue(RoomStage.DAY2_FUND_VOTE.canMoveTo(RoomStage.DAY2_FINAL_RESULT));
    }

    @Test
    void placesDayOneQuizResultBeforeIncomeSettlement() {
        assertTrue(RoomStage.DAY1_QUIZ_RESULT.ordinal() > RoomStage.DAY1_QUIZ.ordinal());
        assertTrue(RoomStage.DAY1_QUIZ_RESULT.ordinal() < RoomStage.DAY1_INCOME_SETTLEMENT.ordinal());
    }
}
