package com.b201.ecovillage.course.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
@Table(name = "course_template_modules")
public class CourseTemplateModule {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "course_template_version_id", nullable = false, updatable = false)
    private UUID courseTemplateVersionId;

    @Column(name = "module_code", nullable = false, updatable = false, length = 40)
    private String moduleCode;

    @Column(name = "recommendation_level", nullable = false, updatable = false, length = 40)
    private String recommendationLevel;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", nullable = false, updatable = false)
    private Map<String, Object> configJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, updatable = false)
    private Instant updatedAt;

    protected CourseTemplateModule() {
    }

    private CourseTemplateModule(
        UUID id,
        UUID courseTemplateVersionId,
        String moduleCode,
        String recommendationLevel,
        int sortOrder,
        Map<String, Object> configJson,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.courseTemplateVersionId = Objects.requireNonNull(
            courseTemplateVersionId,
            "courseTemplateVersionId must not be null"
        );
        this.moduleCode = required(moduleCode, "moduleCode");
        this.recommendationLevel = required(
            recommendationLevel,
            "recommendationLevel"
        );
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must not be negative");
        }
        this.sortOrder = sortOrder;
        this.configJson = new LinkedHashMap<>(
            Objects.requireNonNull(configJson, "configJson must not be null")
        );
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = createdAt;
    }

    public static CourseTemplateModule published(
        UUID id,
        UUID courseTemplateVersionId,
        String moduleCode,
        String recommendationLevel,
        int sortOrder,
        Map<String, Object> configJson,
        Instant createdAt
    ) {
        return new CourseTemplateModule(
            id,
            courseTemplateVersionId,
            moduleCode,
            recommendationLevel,
            sortOrder,
            configJson,
            createdAt
        );
    }

    public String getModuleCode() {
        return moduleCode;
    }

    public String getRecommendationLevel() {
        return recommendationLevel;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String label() {
        return String.valueOf(configJson.getOrDefault("label", moduleCode));
    }

    public List<String> prerequisites() {
        Object value = configJson.get("prerequisites");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    public String exclusionImpact() {
        Object value = configJson.get("exclusion_impact");
        return value == null ? null : String.valueOf(value);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
