package com.b201.ecovillage.investment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.investment.domain.InvestmentAsset;

public interface InvestmentAssetRepository extends JpaRepository<InvestmentAsset, UUID> {

    List<InvestmentAsset> findAllByClassroomIdAndScenarioVersionIdAndStatusOrderByDisplayOrder(
        UUID classroomId,
        UUID scenarioVersionId,
        String status
    );

    Optional<InvestmentAsset> findByClassroomIdAndId(UUID classroomId, UUID id);
}
