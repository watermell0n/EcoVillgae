package com.b201.bibim.domain.room;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "room_rules")
public class RoomRulesEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "role_income", nullable = false)
    private long roleIncome;

    @Column(name = "day1_participation_reward", nullable = false)
    private long day1ParticipationReward;

    @Column(name = "day1_correct_reward", nullable = false)
    private long day1CorrectReward;

    @Column(name = "income_tax_bps", nullable = false)
    private int incomeTaxBps;

    protected RoomRulesEntity() {
    }

    public UUID getRoomId() {
        return roomId;
    }

    public long getRoleIncome() {
        return roleIncome;
    }

    public long getDay1ParticipationReward() {
        return day1ParticipationReward;
    }

    public long getDay1CorrectReward() {
        return day1CorrectReward;
    }

    public int getIncomeTaxBps() {
        return incomeTaxBps;
    }
}
