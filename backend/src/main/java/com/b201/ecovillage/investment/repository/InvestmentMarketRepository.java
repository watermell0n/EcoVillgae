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

    List<InvestmentMarket> findAllByClassroomIdOrderByCreatedAtDesc(UUID classroomId);

    /**
     * 학급이 단기와 장기를 동시에 운영하므로 학급만으로는 시장이 하나로 정해지지 않는다.
     * 방식까지 지정해야 어떤 시장인지 확정된다.
     *
     * <p>정산이 끝난 시장이 쌓이므로 살아 있는 시장을 먼저, 그다음 최근에 만든 순으로 준다.
     * 호출하는 쪽은 첫 항목만 쓴다. 단건 조회로 두면 시장이 둘 이상일 때 예외가 난다.
     */
    @Query("""
        select market from InvestmentMarket market
         where market.classroomId = :classroomId
           and market.investmentMode = :investmentMode
         order by case when market.status <> 'CLOSED' then 0 else 1 end, market.createdAt desc
        """)
    List<InvestmentMarket> findByModeLiveFirst(
        @Param("classroomId") UUID classroomId,
        @Param("investmentMode") InvestmentMode investmentMode
    );

    /** 방식별로 살아 있는 시장만 준다. 정산이 끝난 시장은 목록에서 빠진다. */
    @Query("""
        select market from InvestmentMarket market
         where market.classroomId = :classroomId
           and market.status <> 'CLOSED'
         order by market.investmentMode asc
        """)
    List<InvestmentMarket> findAllLiveByClassroomId(@Param("classroomId") UUID classroomId);

    List<InvestmentMarket> findAllByClassroomIdAndStatus(UUID classroomId, String status);

    Optional<InvestmentMarket> findByClassroomIdAndActiveScenarioVersionId(
        UUID classroomId,
        UUID activeScenarioVersionId
    );

    /**
     * 방식마다 지금 살아 있는 시장 하나를 잡는다.
     *
     * <p>정산이 끝난 CLOSED 시장은 제외한다. 단기는 하루 주기로 정산하고 다시 열기 때문에
     * 같은 방식의 CLOSED 시장이 여러 개 쌓인다. 이를 걸러 내지 않으면 새 시나리오를 만들려 할 때
     * 지난 시장이 잡혀 "진행 중인 시장"으로 오해된다. 유니크 조건도 CLOSED를 제외하므로
     * 살아 있는 시장은 방식당 최대 하나다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select market from InvestmentMarket market
         where market.classroomId = :classroomId
           and market.investmentMode = :investmentMode
           and market.status <> 'CLOSED'
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
