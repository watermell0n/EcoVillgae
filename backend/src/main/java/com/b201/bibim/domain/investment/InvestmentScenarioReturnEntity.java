package com.b201.bibim.domain.investment;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "investment_scenario_returns")
public class InvestmentScenarioReturnEntity {

    @Id
    private UUID id;

    @Column(name = "scenario_id", nullable = false)
    private UUID scenarioId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "return_bps", nullable = false)
    private int returnBps;

    protected InvestmentScenarioReturnEntity() {
    }

    public static InvestmentScenarioReturnEntity create(
            UUID scenarioId,
            UUID assetId,
            int returnBps
    ) {
        InvestmentScenarioReturnEntity result = new InvestmentScenarioReturnEntity();
        result.id = UUID.randomUUID();
        result.scenarioId = scenarioId;
        result.assetId = assetId;
        result.returnBps = returnBps;
        return result;
    }
}
