package com.b201.ecovillage.finance.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "loan_policy_versions")
public class LoanPolicyVersion {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(nullable = false, updatable = false)
    private int version;

    @Column(name = "maximum_principal", nullable = false, updatable = false)
    private long maximumPrincipal;

    @Column(name = "interest_rate_bps", nullable = false, updatable = false)
    private int interestRateBps;

    @Column(name = "term_units", nullable = false, updatable = false)
    private int termUnits;

    @Column(name = "installment_count", nullable = false, updatable = false)
    private int installmentCount;

    @Column(name = "grace_units", nullable = false, updatable = false)
    private int graceUnits;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "credit_adjustments", nullable = false, updatable = false)
    private Map<String, Object> creditAdjustments;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private Instant effectiveFrom;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LoanPolicyVersion() {
    }

    private LoanPolicyVersion(
        UUID id,
        UUID classroomId,
        int version,
        long maximumPrincipal,
        int interestRateBps,
        int termUnits,
        int installmentCount,
        int graceUnits,
        Map<String, Object> creditAdjustments,
        Instant effectiveFrom
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        if (version < 1 || maximumPrincipal < 1 || termUnits < 1 || installmentCount < 1 || graceUnits < 0) {
            throw new IllegalArgumentException("loan policy values are invalid");
        }
        if (interestRateBps < 0 || interestRateBps > 10_000) {
            throw new IllegalArgumentException("interestRateBps must be between 0 and 10000");
        }
        this.version = version;
        this.maximumPrincipal = maximumPrincipal;
        this.interestRateBps = interestRateBps;
        this.termUnits = termUnits;
        this.installmentCount = installmentCount;
        this.graceUnits = graceUnits;
        this.creditAdjustments = new LinkedHashMap<>(
            creditAdjustments == null ? Map.of() : creditAdjustments
        );
        this.status = "ACTIVE";
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
        this.createdAt = effectiveFrom;
    }

    public static LoanPolicyVersion activate(
        UUID id,
        UUID classroomId,
        int version,
        long maximumPrincipal,
        int interestRateBps,
        int termUnits,
        int installmentCount,
        int graceUnits,
        Map<String, Object> creditAdjustments,
        Instant effectiveFrom
    ) {
        return new LoanPolicyVersion(
            id,
            classroomId,
            version,
            maximumPrincipal,
            interestRateBps,
            termUnits,
            installmentCount,
            graceUnits,
            creditAdjustments,
            effectiveFrom
        );
    }

    public void supersede(Instant changedAt) {
        if (!"ACTIVE".equals(status) || supersededAt != null) {
            throw new IllegalStateException("only an active loan policy can be superseded");
        }
        status = "SUPERSEDED";
        supersededAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public int getVersion() {
        return version;
    }

    public long getMaximumPrincipal() {
        return maximumPrincipal;
    }

    public int getInterestRateBps() {
        return interestRateBps;
    }

    public int getTermUnits() {
        return termUnits;
    }

    public int getInstallmentCount() {
        return installmentCount;
    }

    public int getGraceUnits() {
        return graceUnits;
    }

    public Map<String, Object> getCreditAdjustments() {
        return Map.copyOf(creditAdjustments);
    }

    public String getStatus() {
        return status;
    }
}
