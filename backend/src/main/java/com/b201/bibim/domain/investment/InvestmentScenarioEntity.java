package com.b201.bibim.domain.investment;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "investment_scenarios")
public class InvestmentScenarioEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InvestmentScenarioEntity() {
    }

    public static InvestmentScenarioEntity ready(UUID roomId) {
        InvestmentScenarioEntity scenario = new InvestmentScenarioEntity();
        scenario.id = UUID.randomUUID();
        scenario.roomId = roomId;
        scenario.code = "DAY1_BALANCED";
        scenario.name = "1일차 기본 시장 시나리오";
        scenario.status = "READY";
        scenario.createdAt = Instant.now();
        return scenario;
    }

    public void lock() {
        if ("READY".equals(status)) {
            status = "LOCKED";
            lockedAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getRoomId() { return roomId; }
    public String getStatus() { return status; }
    public Instant getLockedAt() { return lockedAt; }
}
