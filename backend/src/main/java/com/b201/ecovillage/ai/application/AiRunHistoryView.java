package com.b201.ecovillage.ai.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AiRunHistoryView(
    UUID aiRunId,
    UUID operationId,
    String featureType,
    String runType,
    String triggerType,
    String status,
    Instant requestedAt,
    Instant completedAt,
    Instant dataAsOf,
    String promptVersion,
    String provider,
    String modelId,
    String safetyStatus,
    boolean fallbackUsed,
    String fallbackCode,
    String severity,
    List<String> validationCodes,
    List<String> evidenceCodes,
    Map<String, String> ruleVersions,
    List<Proposal> proposals
) {

    public record Proposal(
        UUID proposalId,
        String proposalType,
        String sourceType,
        String status,
        String severity,
        String metricCode,
        String direction,
        String dedupeKey,
        String summary,
        String contextType,
        String targetType,
        UUID targetId,
        Long targetVersion,
        List<ProposedValue> proposedValues,
        List<AllowedRange> allowedRanges,
        Instant createdAt,
        Instant expiresAt,
        Instant statusChangedAt,
        String decisionStatus,
        Instant decidedAt,
        Integer policyVersion,
        UUID appliedPolicyVersionId,
        long stateVersion
    ) {
    }

    public record ProposedValue(
        String actionId,
        String targetType,
        UUID targetId,
        Long proposedValue,
        String unit,
        String label
    ) {
    }

    public record AllowedRange(
        String actionId,
        Long allowedMinValue,
        Long allowedMaxValue
    ) {
    }
}
