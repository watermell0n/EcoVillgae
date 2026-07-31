package com.b201.ecovillage.classroom.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payslips")
public class Payslip {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "classroom_student_id", nullable = false, updatable = false)
    private UUID classroomStudentId;

    @Column(name = "salary_role_id", updatable = false)
    private UUID salaryRoleId;

    @Column(name = "period_key", nullable = false, updatable = false, length = 40)
    private String periodKey;

    @Column(name = "gross_amount", nullable = false, updatable = false)
    private long grossAmount;

    @Column(name = "deduction_amount", nullable = false, updatable = false)
    private long deductionAmount;

    @Column(name = "tax_amount", nullable = false, updatable = false)
    private long taxAmount;

    @Column(name = "net_amount", nullable = false, updatable = false)
    private long netAmount;

    @Column(name = "policy_snapshot_id", nullable = false, updatable = false)
    private UUID policySnapshotId;

    @Column(name = "transaction_group_id", nullable = false, updatable = false)
    private UUID transactionGroupId;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Payslip() {
    }

    private Payslip(
        UUID id,
        UUID classroomId,
        UUID classroomStudentId,
        UUID salaryRoleId,
        String periodKey,
        long grossAmount,
        long deductionAmount,
        long taxAmount,
        UUID policySnapshotId,
        UUID transactionGroupId,
        Instant issuedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.classroomStudentId = Objects.requireNonNull(
            classroomStudentId,
            "classroomStudentId must not be null"
        );
        this.salaryRoleId = Objects.requireNonNull(
            salaryRoleId,
            "salaryRoleId must not be null"
        );
        this.periodKey = requiredText(periodKey, 40, "periodKey");
        if (grossAmount < 0 || deductionAmount < 0 || taxAmount < 0) {
            throw new IllegalArgumentException("payslip amounts must not be negative");
        }
        long withheld = Math.addExact(deductionAmount, taxAmount);
        if (withheld > grossAmount) {
            throw new IllegalArgumentException("deductions and tax exceed gross amount");
        }
        this.grossAmount = grossAmount;
        this.deductionAmount = deductionAmount;
        this.taxAmount = taxAmount;
        this.netAmount = grossAmount - withheld;
        this.policySnapshotId = Objects.requireNonNull(
            policySnapshotId,
            "policySnapshotId must not be null"
        );
        this.transactionGroupId = Objects.requireNonNull(
            transactionGroupId,
            "transactionGroupId must not be null"
        );
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        this.createdAt = issuedAt;
    }

    public static Payslip issue(
        UUID id,
        UUID classroomId,
        UUID classroomStudentId,
        UUID salaryRoleId,
        String periodKey,
        long grossAmount,
        long deductionAmount,
        long taxAmount,
        UUID policySnapshotId,
        UUID transactionGroupId,
        Instant issuedAt
    ) {
        return new Payslip(
            id,
            classroomId,
            classroomStudentId,
            salaryRoleId,
            periodKey,
            grossAmount,
            deductionAmount,
            taxAmount,
            policySnapshotId,
            transactionGroupId,
            issuedAt
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomStudentId() {
        return classroomStudentId;
    }

    public String getPeriodKey() {
        return periodKey;
    }

    public long getGrossAmount() {
        return grossAmount;
    }

    public long getDeductionAmount() {
        return deductionAmount;
    }

    public long getTaxAmount() {
        return taxAmount;
    }

    public long getNetAmount() {
        return netAmount;
    }

    public UUID getPolicySnapshotId() {
        return policySnapshotId;
    }

    public UUID getTransactionGroupId() {
        return transactionGroupId;
    }

    private static String requiredText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}
