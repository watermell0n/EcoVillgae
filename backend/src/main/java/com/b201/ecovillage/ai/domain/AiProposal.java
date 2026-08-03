package com.b201.ecovillage.ai.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_proposals")
public class AiProposal {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "ai_run_id", nullable = false, updatable = false)
    private UUID aiRunId;

    @Column(name = "proposal_type", nullable = false, updatable = false, length = 40)
    private String proposalType;

    @Column(name = "source_type", nullable = false, updatable = false, length = 40)
    private String sourceType;

    @Column(length = 20)
    private String severity;

    @Column(name = "metric_code", length = 80)
    private String metricCode;

    @Column(length = 20)
    private String direction;

    @Column(name = "dedupe_key", length = 200)
    private String dedupeKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "proposed_values", nullable = false)
    private Map<String, Object> proposedValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_ranges", nullable = false, updatable = false)
    private Map<String, Object> allowedRanges;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "applied_policy_version_id")
    private UUID appliedPolicyVersionId;

    @Column(nullable = false, length = 40)
    private String status;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiProposal() {
    }

    public static AiProposal setup(
        UUID id,
        UUID classroomId,
        UUID aiRunId,
        String severity,
        Map<String, Object> proposedValues,
        Map<String, Object> allowedRanges,
        Instant now
    ) {
        AiProposal proposal = new AiProposal();
        proposal.id = Objects.requireNonNull(id);
        proposal.classroomId = Objects.requireNonNull(classroomId);
        proposal.aiRunId = Objects.requireNonNull(aiRunId);
        proposal.proposalType = "SETUP_RECOMMENDATION";
        proposal.sourceType = "SETUP_PRECHECK";
        proposal.severity = Objects.requireNonNull(severity);
        proposal.proposedValues = new LinkedHashMap<>(proposedValues);
        proposal.allowedRanges = new LinkedHashMap<>(allowedRanges);
        proposal.expiresAt = now.plusSeconds(30L * 24L * 60L * 60L);
        proposal.status = "AWAITING_TEACHER";
        proposal.statusChangedAt = now;
        proposal.createdAt = now;
        proposal.updatedAt = now;
        return proposal;
    }

    public static AiProposal balancing(
        UUID id,
        UUID classroomId,
        UUID aiRunId,
        String sourceType,
        String severity,
        String metricCode,
        String direction,
        String dedupeKey,
        Map<String, Object> proposedValues,
        Map<String, Object> allowedRanges,
        Instant now
    ) {
        AiProposal proposal = new AiProposal();
        proposal.id = Objects.requireNonNull(id);
        proposal.classroomId = Objects.requireNonNull(classroomId);
        proposal.aiRunId = Objects.requireNonNull(aiRunId);
        proposal.proposalType = "BALANCING_RECOMMENDATION";
        proposal.sourceType = Objects.requireNonNull(sourceType);
        proposal.severity = Objects.requireNonNull(severity);
        proposal.metricCode = metricCode;
        proposal.direction = direction;
        proposal.dedupeKey = dedupeKey;
        proposal.proposedValues = new LinkedHashMap<>(proposedValues);
        proposal.allowedRanges = new LinkedHashMap<>(allowedRanges);
        proposal.expiresAt = now.plusSeconds(14L * 24L * 60L * 60L);
        proposal.status = "AWAITING_TEACHER";
        proposal.statusChangedAt = now;
        proposal.createdAt = now;
        proposal.updatedAt = now;
        return proposal;
    }

    public void acknowledge(Instant now) {
        transition("ACKNOWLEDGED", now);
    }

    public void reject(Instant now) {
        transition("REJECTED", now);
    }

    public void approve(Instant now) {
        transition("APPROVED", now);
    }

    public void replaceProposedValues(Map<String, Object> values, Instant now) {
        this.proposedValues = new LinkedHashMap<>(Objects.requireNonNull(values));
        this.updatedAt = Objects.requireNonNull(now);
    }

    public void applied(UUID policyVersionId, Instant now) {
        this.appliedPolicyVersionId = policyVersionId;
        transition("APPLIED", now);
    }

    public void applyFailed(Instant now) {
        transition("APPLY_FAILED", now);
    }

    private void transition(String status, Instant now) {
        this.status = status;
        this.statusChangedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public UUID getAiRunId() {
        return aiRunId;
    }

    public String getProposalType() {
        return proposalType;
    }

    public String getSeverity() {
        return severity;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getMetricCode() {
        return metricCode;
    }

    public String getDirection() {
        return direction;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public Map<String, Object> getProposedValues() {
        return Map.copyOf(proposedValues);
    }

    public Map<String, Object> getAllowedRanges() {
        return Map.copyOf(allowedRanges);
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public UUID getAppliedPolicyVersionId() {
        return appliedPolicyVersionId;
    }

    public String getStatus() {
        return status;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
