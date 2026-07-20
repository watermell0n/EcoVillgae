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

    @Column(name = "deposit_interest_bps", nullable = false)
    private int depositInterestBps;

    @Column(name = "financial_tax_bps", nullable = false)
    private int financialTaxBps;

    @Column(name = "day2_participation_reward", nullable = false)
    private long day2ParticipationReward;

    @Column(name = "day2_correct_reward", nullable = false)
    private long day2CorrectReward;

    @Column(name = "inflation_bps", nullable = false)
    private int inflationBps;

    @Column(name = "deposit_min_amount", nullable = false)
    private long depositMinAmount;

    @Column(name = "deposit_unit", nullable = false)
    private long depositUnit;

    @Column(name = "investment_min_amount", nullable = false)
    private long investmentMinAmount;

    @Column(name = "investment_unit", nullable = false)
    private long investmentUnit;

    @Column(name = "initial_fund_amount", nullable = false)
    private long initialFundAmount;

    protected RoomRulesEntity() {
    }

    public static RoomRulesEntity defaults(UUID roomId) {
        RoomRulesEntity rules = new RoomRulesEntity();
        rules.id = UUID.randomUUID();
        rules.roomId = roomId;
        rules.roleIncome = 150;
        rules.day1ParticipationReward = 0;
        rules.day1CorrectReward = 5;
        rules.incomeTaxBps = 1000;
        rules.depositInterestBps = 1000;
        rules.financialTaxBps = 1000;
        rules.day2ParticipationReward = 0;
        rules.day2CorrectReward = 5;
        rules.inflationBps = 1000;
        rules.depositMinAmount = 20;
        rules.depositUnit = 10;
        rules.investmentMinAmount = 10;
        rules.investmentUnit = 10;
        rules.initialFundAmount = 0;
        return rules;
    }

    public void updateEconomyRules(
            long roleIncome,
            long day1CorrectReward,
            int incomeTaxBps,
            int depositInterestBps,
            int financialTaxBps,
            long day2CorrectReward,
            int inflationBps,
            long depositMinAmount,
            long depositUnit,
            long investmentMinAmount,
            long investmentUnit
    ) {
        this.roleIncome = roleIncome;
        this.day1CorrectReward = day1CorrectReward;
        this.incomeTaxBps = incomeTaxBps;
        this.depositInterestBps = depositInterestBps;
        this.financialTaxBps = financialTaxBps;
        this.day2CorrectReward = day2CorrectReward;
        this.inflationBps = inflationBps;
        this.depositMinAmount = depositMinAmount;
        this.depositUnit = depositUnit;
        this.investmentMinAmount = investmentMinAmount;
        this.investmentUnit = investmentUnit;
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

    public int getDepositInterestBps() {
        return depositInterestBps;
    }

    public int getFinancialTaxBps() {
        return financialTaxBps;
    }

    public long getDay2ParticipationReward() {
        return day2ParticipationReward;
    }

    public long getDay2CorrectReward() {
        return day2CorrectReward;
    }

    public int getInflationBps() {
        return inflationBps;
    }

    public long getDepositMinAmount() { return depositMinAmount; }
    public long getDepositUnit() { return depositUnit; }
    public long getInvestmentMinAmount() { return investmentMinAmount; }
    public long getInvestmentUnit() { return investmentUnit; }
    public long getInitialFundAmount() { return initialFundAmount; }
    public void updateInitialFundAmount(long amount) { this.initialFundAmount = amount; }
}
