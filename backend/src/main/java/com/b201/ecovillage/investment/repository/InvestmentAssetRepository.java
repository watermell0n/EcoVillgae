package com.b201.ecovillage.investment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.b201.ecovillage.investment.domain.InvestmentAsset;

public interface InvestmentAssetRepository extends JpaRepository<InvestmentAsset, UUID> {

    List<InvestmentAsset> findAllByClassroomIdAndScenarioVersionIdAndStatusOrderByDisplayOrder(
        UUID classroomId,
        UUID scenarioVersionId,
        String status
    );

    Optional<InvestmentAsset> findByClassroomIdAndId(UUID classroomId, UUID id);

    /**
     * 이 학급이 지금까지 쓴 기업 이름을 최근 것부터 준다. 새 시나리오에서 같은 이름을
     * 다시 짓지 않도록 AI에 함께 보낸다.
     */
    @Query("""
        select asset.name from InvestmentAsset asset
         where asset.classroomId = :classroomId
         order by asset.createdAt desc
        """)
    List<String> findRecentNames(@Param("classroomId") UUID classroomId);
}
