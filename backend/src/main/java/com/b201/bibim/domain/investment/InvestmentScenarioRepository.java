package com.b201.bibim.domain.investment;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvestmentScenarioRepository
        extends JpaRepository<InvestmentScenarioEntity, UUID> {

    Optional<InvestmentScenarioEntity> findByRoomId(UUID roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select scenario from InvestmentScenarioEntity scenario where scenario.roomId = :roomId")
    Optional<InvestmentScenarioEntity> findByRoomIdForUpdate(@Param("roomId") UUID roomId);
}
