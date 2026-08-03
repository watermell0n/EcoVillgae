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
    UUID sourceProposalId
) {
}
