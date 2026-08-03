package com.b201.ecovillage.investment.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.investment.domain.InvestmentMarket;

public interface InvestmentMarketRepository extends JpaRepository<InvestmentMarket, UUID> {

    Optional<InvestmentMarket> findByClassroomIdAndStatus(UUID classroomId, String status);
}
