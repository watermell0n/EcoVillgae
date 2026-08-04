package com.b201.ecovillage.investment.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.b201.ecovillage.investment.domain.InvestmentMarket;

public interface InvestmentMarketRepository extends JpaRepository<InvestmentMarket, UUID> {

    Optional<InvestmentMarket> findByClassroomIdAndStatus(UUID classroomId, String status);

    Optional<InvestmentMarket> findByClassroomId(UUID classroomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select market from InvestmentMarket market where market.classroomId = :classroomId")
    Optional<InvestmentMarket> findForUpdate(@Param("classroomId") UUID classroomId);
}
