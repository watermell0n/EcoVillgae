package com.b201.ecovillage.investment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 시장에 적용된 가격 시나리오의 한 판본이다. 가격 변동표는 이 판본에 묶여 고정된다.
 */
@Entity
@Table(name = "investment_scenario_versions")
public class InvestmentScenarioVersion {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "investment_market_id", nullable = false, updatable = false)
    private UUID investmentMarketId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "scenario_seed", length = 128)
    private String scenarioSeed;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InvestmentScenarioVersion() {
    }

    public static InvestmentScenarioVersion published(
        UUID id,
        UUID classroomId,
        UUID investmentMarketId,
        int version,
        String name,
        Instant now
    ) {
        InvestmentScenarioVersion scenario = new InvestmentScenarioVersion();
        scenario.id = Objects.requireNonNull(id);
        scenario.classroomId = Objects.requireNonNull(classroomId);
        scenario.investmentMarketId = Objects.requireNonNull(investmentMarketId);
        scenario.version = version;
        scenario.name = Objects.requireNonNull(name);
        scenario.status = PUBLISHED;
        scenario.publishedAt = now;
        scenario.createdAt = now;
        return scenario;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInvestmentMarketId() {
        return investmentMarketId;
    }

    public int getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }
}
