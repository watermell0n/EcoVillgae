package com.b201.ecovillage.classroom.domain;

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
@Table(name = "salary_roles")
public class SalaryRole {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "role_code", nullable = false, updatable = false, length = 40)
    private String roleCode;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "gross_amount", nullable = false)
    private long grossAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "deductions_json", nullable = false)
    private Map<String, Object> deductions;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

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

    protected SalaryRole() {
    }

    private SalaryRole(
        UUID id,
        UUID classroomId,
        String roleCode,
        String name,
        long grossAmount,
        Map<String, Object> deductions,
        int sortOrder,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.roleCode = Objects.requireNonNull(roleCode, "roleCode must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.grossAmount = grossAmount;
        this.deductions = new LinkedHashMap<>(deductions);
        this.sortOrder = sortOrder;
        this.status = "ACTIVE";
        this.statusChangedAt = createdAt;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static SalaryRole create(
        UUID id,
        UUID classroomId,
        String roleCode,
        String name,
        long grossAmount,
        Map<String, Object> deductions,
        int sortOrder,
        Instant createdAt
    ) {
        return new SalaryRole(
            id,
            classroomId,
            roleCode,
            name,
            grossAmount,
            deductions,
            sortOrder,
            createdAt
        );
    }

    public void update(
        String name,
        long grossAmount,
        Map<String, Object> deductions,
        int sortOrder,
        Instant changedAt
    ) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.grossAmount = grossAmount;
        this.deductions = new LinkedHashMap<>(deductions);
        this.sortOrder = sortOrder;
        this.status = "ACTIVE";
        this.statusChangedAt = changedAt;
        this.updatedAt = changedAt;
    }

    public void deactivate(Instant changedAt) {
        this.status = "INACTIVE";
        this.statusChangedAt = changedAt;
        this.updatedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getName() {
        return name;
    }

    public long getGrossAmount() {
        return grossAmount;
    }

    public Map<String, Object> getDeductions() {
        return Map.copyOf(deductions);
    }

    public String getStatus() {
        return status;
    }

    public long getStateVersion() {
        return stateVersion;
    }
}
