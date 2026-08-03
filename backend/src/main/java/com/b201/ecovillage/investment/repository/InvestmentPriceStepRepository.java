package com.b201.ecovillage.investment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.investment.domain.InvestmentPriceStep;

public interface InvestmentPriceStepRepository extends JpaRepository<InvestmentPriceStep, UUID> {

    /**
     * 이미 공개된 단계만 오름차순으로 가져온다. 미공개 단계는 절대 포함하지 않는다.
     */
    List<InvestmentPriceStep>
        findAllByClassroomIdAndInvestmentAssetIdAndRevealedAtIsNotNullOrderByStepNumber(
            UUID classroomId,
            UUID investmentAssetId
        );

    Optional<InvestmentPriceStep> findByClassroomIdAndInvestmentAssetIdAndStepNumber(
        UUID classroomId,
        UUID investmentAssetId,
        int stepNumber
    );
}
