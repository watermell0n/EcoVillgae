package com.b201.ecovillage.ai.repository;

import java.util.Optional;
import java.util.List;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.b201.ecovillage.ai.domain.EconomicEventDraft;

public interface EconomicEventDraftRepository extends JpaRepository<EconomicEventDraft, UUID> {

    Optional<EconomicEventDraft> findByClassroomIdAndId(UUID classroomId, UUID id);

    boolean existsByClassroomIdAndSourceProposalId(UUID classroomId, UUID sourceProposalId);

    /**
     * 가격 단계에 살아 있는 뉴스가 있는지 본다. 거절된 초안은 자리를 비운 것으로 취급한다.
     */
    boolean existsByClassroomIdAndInvestmentPriceStepIdAndStatusNot(
        UUID classroomId,
        UUID investmentPriceStepId,
        String status
    );

    boolean existsByClassroomIdAndInvestmentPriceStepIdAndStatusIn(
        UUID classroomId,
        UUID investmentPriceStepId,
        Collection<String> statuses
    );

    List<EconomicEventDraft> findAllByClassroomIdAndInvestmentPriceStepIdInAndStatusIn(
        UUID classroomId,
        Collection<UUID> investmentPriceStepIds,
        Collection<String> statuses
    );
}
