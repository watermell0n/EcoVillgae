package com.b201.ecovillage.investment.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.investment.domain.InvestmentScenarioVersion;

public interface InvestmentScenarioVersionRepository
    extends JpaRepository<InvestmentScenarioVersion, UUID> {

    Optional<InvestmentScenarioVersion> findByClassroomIdAndId(UUID classroomId, UUID id);
}
