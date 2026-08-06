package com.b201.ecovillage.investment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.b201.ecovillage.investment.domain.InvestmentMarket;
import com.b201.ecovillage.investment.domain.InvestmentMode;

public interface InvestmentMarketRepository extends JpaRepository<InvestmentMarket, UUID> {

    /**
     * 학급이 단기와 장기를 동시에 운영하므로 학급만으로는 시장이 하나로 정해지지 않는다.
     * 방식까지 지정해야 어떤 시장인지 확정된다.
     */
    Optional<InvestmentMarket> findByClassroomIdAndInvestmentMode(
        UUID classroomId,
        InvestmentMode investmentMode
    );

    Optional<InvestmentMarket> findByClassroomIdAndInvestmentModeAndStatus(
        UUID classroomId,
        InvestmentMode investmentMode,
        String status
    );

    List<InvestmentMarket> findAllByClassroomIdOrderByInvestmentModeAsc(UUID classroomId);

    List<InvestmentMarket> findAllByClassroomIdAndStatus(UUID classroomId, String status);

    Optional<InvestmentMarket> findByClassroomIdAndActiveScenarioVersionId(
        UUID classroomId,
        UUID activeScenarioVersionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select market from InvestmentMarket market
         where market.classroomId = :classroomId
           and market.investmentMode = :investmentMode
        """)
    Optional<InvestmentMarket> findForUpdate(
        @Param("classroomId") UUID classroomId,
        @Param("investmentMode") InvestmentMode investmentMode
    );

    /**
     * 학생 주문은 종목만 보내므로 방식을 알 수 없다. 종목이 속한 시나리오로 시장을 되짚어
     * 찾으면 학생이 어느 시장에 주문했는지 서버가 판단할 수 있다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select market from InvestmentMarket market
         where market.classroomId = :classroomId
           and market.activeScenarioVersionId = :scenarioVersionId
        """)
    Optional<InvestmentMarket> findForUpdateByActiveScenario(
        @Param("classroomId") UUID classroomId,
        @Param("scenarioVersionId") UUID scenarioVersionId
    );
}
