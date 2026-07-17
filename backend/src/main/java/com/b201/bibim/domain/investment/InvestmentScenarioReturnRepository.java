package com.b201.bibim.domain.investment;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentScenarioReturnRepository
        extends JpaRepository<InvestmentScenarioReturnEntity, UUID> {

    List<InvestmentScenarioReturnEntity> findAllByScenarioId(UUID scenarioId);
}
