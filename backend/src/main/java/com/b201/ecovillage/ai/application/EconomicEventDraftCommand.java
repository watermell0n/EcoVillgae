package com.b201.ecovillage.ai.application;

import java.util.UUID;

import com.b201.ecovillage.ai.contract.EconomicEventContract;

public record EconomicEventDraftCommand(
    String teacherSubject,
    UUID classroomId,
    UUID requestId,
    UUID correlationId,
    EconomicEventContract.TemplateType templateType,
    EconomicEventContract.InterestRateScope interestRateScope,
    String teacherContext,
    UUID sourceProposalId,
    /**
     * INVESTMENT_NEWS에서 뉴스를 붙일 투자 방식이다. 학급이 단기와 장기를 함께 운영하므로
     * 교사가 어느 쪽 이야기를 만드는지 지정해야 한다. 다른 템플릿에서는 null이다.
     */
    String investmentMode
) {
}
