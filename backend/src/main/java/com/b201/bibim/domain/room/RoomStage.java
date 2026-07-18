package com.b201.bibim.domain.room;

import java.util.Arrays;
import java.util.Optional;

public enum RoomStage {
    DAY1_LOBBY(1, "DAY1_ACTIVE", "GUIDED"),
    DAY1_PROFILE(1, "DAY1_ACTIVE", "GUIDED"),
    DAY1_QUIZ(1, "DAY1_ACTIVE", "GUIDED"),
    DAY1_QUIZ_RESULT(1, "DAY1_ACTIVE", "GUIDED"),
    DAY1_INCOME_SETTLEMENT(1, "DAY1_ACTIVE", "GUIDED"),
    DAY1_PAYSLIP_GUIDED(1, "DAY1_ACTIVE", "GUIDED"),
    DAY1_KNOWLEDGE_GUIDED(1, "DAY1_ACTIVE", "GUIDED"),
    DAY1_FINANCE_GUIDED(1, "DAY1_ACTIVE", "GUIDED"),
    DAY1_SHOP_GUIDED(1, "DAY1_ACTIVE", "GUIDED"),
    DAY1_FREE_ACTIVITY(1, "DAY1_ACTIVE", "FREE"),
    DAY1_SUMMARY(1, "DAY1_ACTIVE", "GUIDED"),
    DAY1_CLOSED(1, "DAY1_CLOSED", "CLOSED"),
    DAY2_LOGIN(2, "DAY2_ACTIVE", "GUIDED"),
    DAY2_SETTLEMENT_WAIT(2, "DAY2_ACTIVE", "GUIDED"),
    DAY2_FINANCE_SETTLEMENT(2, "DAY2_ACTIVE", "GUIDED"),
    DAY2_RESULT_GUIDED(2, "DAY2_ACTIVE", "GUIDED"),
    DAY2_QUIZ(2, "DAY2_ACTIVE", "GUIDED"),
    DAY2_QUIZ_RESULT(2, "DAY2_ACTIVE", "GUIDED"),
    DAY2_QUIZ_SETTLEMENT(2, "DAY2_ACTIVE", "GUIDED"),
    DAY2_TAX_INFLATION_GUIDED(2, "DAY2_ACTIVE", "GUIDED"),
    DAY2_INFLATION_APPLY(2, "DAY2_ACTIVE", "GUIDED"),
    DAY2_FINAL_SHOP(2, "DAY2_ACTIVE", "FREE"),
    DAY2_FUND_COST(2, "DAY2_ACTIVE", "GUIDED"),
    DAY2_FUND_VOTE(2, "DAY2_ACTIVE", "GUIDED"),
    DAY2_FUND_RESULT(2, "DAY2_ACTIVE", "GUIDED"),
    DAY2_FINAL_RESULT(2, "DAY2_ACTIVE", "GUIDED"),
    DAY2_SURVEY(2, "DAY2_ACTIVE", "GUIDED"),
    DAY2_COMPLETED(2, "COMPLETED", "CLOSED");

    private final int day;
    private final String roomStatus;
    private final String mode;

    RoomStage(int day, String roomStatus, String mode) {
        this.day = day;
        this.roomStatus = roomStatus;
        this.mode = mode;
    }

    public int day() {
        return day;
    }

    public String roomStatus() {
        return roomStatus;
    }

    public String mode() {
        return mode;
    }

    public boolean canMoveTo(RoomStage target) {
        return target != null && this != target && day == target.day;
    }

    public static Optional<RoomStage> find(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(stage -> stage.name().equals(value))
                .findFirst();
    }
}
