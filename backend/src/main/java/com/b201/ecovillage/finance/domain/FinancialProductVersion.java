package com.b201.ecovillage.finance.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "financial_product_versions")
public class FinancialProductVersion {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "financial_product_id", nullable = false, updatable = false)
    private UUID financialProductId;

    @Column(nullable = false, updatable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, updatable = false, length = 40)
    private FinancialProductType productType;

    @Column(name = "term_units", nullable = false, updatable = false)
    private int termUnits;

    @Column(name = "interest_rate_bps", nullable = false, updatable = false)
    private int interestRateBps;

    @Column(name = "early_termination_rate_bps", nullable = false, updatable = false)
    private int earlyTerminationRateBps;

    @Column(name = "installment_count", updatable = false)
    private Integer installmentCount;

    @Column(name = "installment_amount", updatable = false)
    private Long installmentAmount;

    @Column(name = "minimum_amount", nullable = false, updatable = false)
    private long minimumAmount;

    @Column(name = "maximum_amount", nullable = false, updatable = false)
    private long maximumAmount;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FinancialProductVersion() {
    }

    private FinancialProductVersion(
        UUID id,
        UUID classroomId,
        UUID financialProductId,
        int version,
        FinancialProductType productType,
        int termUnits,
        int interestRateBps,
        int earlyTerminationRateBps,
        Integer installmentCount,
        Long installmentAmount,
        long minimumAmount,
        long maximumAmount,
        String status,
        Instant publishedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.financialProductId = Objects.requireNonNull(
            financialProductId,
            "financialProductId must not be null"
        );
        if (version < 1 || termUnits < 1) {
            throw new IllegalArgumentException("version and termUnits must be positive");
        }
        this.version = version;
        this.productType = Objects.requireNonNull(productType, "productType must not be null");
        this.termUnits = termUnits;
        this.interestRateBps = rate(interestRateBps, "interestRateBps");
        this.earlyTerminationRateBps = rate(
            earlyTerminationRateBps,
            "earlyTerminationRateBps"
        );
        if (minimumAmount <= 0 || maximumAmount < minimumAmount) {
            throw new IllegalArgumentException("product amount range is invalid");
        }
        if (
            productType == FinancialProductType.DEPOSIT
                && (installmentCount != null || installmentAmount != null)
        ) {
            throw new IllegalArgumentException("deposit cannot have installments");
        }
        if (
            productType == FinancialProductType.SAVINGS
                && (
                    installmentCount == null
                        || installmentCount < 2
                        || installmentCount > 12
                        || installmentAmount == null
                        || installmentAmount <= 0
                )
        ) {
            throw new IllegalArgumentException("savings installment terms are invalid");
        }
        this.installmentCount = installmentCount;
        this.installmentAmount = installmentAmount;
        this.minimumAmount = minimumAmount;
        this.maximumAmount = maximumAmount;
        this.status = requiredStatus(status);
        this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt must not be null");
        this.createdAt = publishedAt;
    }

    public static FinancialProductVersion issue(
        UUID id,
        UUID classroomId,
        UUID financialProductId,
        int version,
        FinancialProductType productType,
        int termUnits,
        int interestRateBps,
        int earlyTerminationRateBps,
        Integer installmentCount,
        Long installmentAmount,
        long minimumAmount,
        long maximumAmount,
        boolean published,
        Instant issuedAt
    ) {
        return new FinancialProductVersion(
            id,
            classroomId,
            financialProductId,
            version,
            productType,
            termUnits,
            interestRateBps,
            earlyTerminationRateBps,
            installmentCount,
            installmentAmount,
            minimumAmount,
            maximumAmount,
            published ? "PUBLISHED" : "DRAFT",
            issuedAt
        );
    }

    public boolean accepts(long amount) {
        return amount >= minimumAmount && amount <= maximumAmount;
    }

    public boolean isPublishedDeposit() {
        return productType == FinancialProductType.DEPOSIT
            && "PUBLISHED".equals(status);
    }

    public boolean isPublished() {
        return "PUBLISHED".equals(status);
    }

    public void supersede(Instant changedAt) {
        if (!"PUBLISHED".equals(status) || retiredAt != null) {
            throw new IllegalStateException("only a published financial product version can be superseded");
        }
        status = "SUPERSEDED";
        retiredAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getFinancialProductId() {
        return financialProductId;
    }

    public int getVersion() {
        return version;
    }

    public FinancialProductType getProductType() {
        return productType;
    }

    public int getTermUnits() {
        return termUnits;
    }

    public int getInterestRateBps() {
        return interestRateBps;
    }

    public int getEarlyTerminationRateBps() {
        return earlyTerminationRateBps;
    }

    public Integer getInstallmentCount() {
        return installmentCount;
    }

    public Long getInstallmentAmount() {
        return installmentAmount;
    }

    public long getMinimumAmount() {
        return minimumAmount;
    }

    public long getMaximumAmount() {
        return maximumAmount;
    }

    private static int rate(int value, String field) {
        if (value < 0 || value > 10_000) {
            throw new IllegalArgumentException(field + " must be between 0 and 10000");
        }
        return value;
    }

    private static String requiredStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        return value;
    }
}
