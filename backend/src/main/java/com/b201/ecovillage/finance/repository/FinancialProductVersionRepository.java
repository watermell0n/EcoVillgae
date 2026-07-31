package com.b201.ecovillage.finance.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.finance.domain.FinancialProductVersion;

public interface FinancialProductVersionRepository
    extends JpaRepository<FinancialProductVersion, UUID> {

    Optional<FinancialProductVersion>
        findTopByClassroomIdAndFinancialProductIdAndStatusOrderByVersionDesc(
            UUID classroomId,
            UUID financialProductId,
            String status
        );

    Optional<FinancialProductVersion>
        findTopByClassroomIdAndFinancialProductIdOrderByVersionDesc(
            UUID classroomId,
            UUID financialProductId
        );
}
