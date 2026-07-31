package com.b201.ecovillage.course.domain;

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
@Table(name = "course_template_versions")
public class CourseTemplateVersion {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "course_template_id", nullable = false, updatable = false)
    private UUID courseTemplateId;

    @Column(nullable = false, updatable = false)
    private int version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "difficulty_profile", nullable = false, updatable = false)
    private Map<String, Object> difficultyProfile;

    @Column(nullable = false, updatable = false, length = 40)
    private String status;

    @Column(name = "published_at", updatable = false)
    private Instant publishedAt;

    @Column(name = "retired_at", updatable = false)
    private Instant retiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, updatable = false)
    private Instant updatedAt;

    protected CourseTemplateVersion() {
    }

    private CourseTemplateVersion(
        UUID id,
        UUID courseTemplateId,
        int version,
        Map<String, Object> difficultyProfile,
        Instant publishedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.courseTemplateId = Objects.requireNonNull(
            courseTemplateId,
            "courseTemplateId must not be null"
        );
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        this.version = version;
        this.difficultyProfile = new LinkedHashMap<>(
            Objects.requireNonNull(difficultyProfile, "difficultyProfile must not be null")
        );
        this.status = "PUBLISHED";
        this.publishedAt = Objects.requireNonNull(
            publishedAt,
            "publishedAt must not be null"
        );
        this.createdAt = publishedAt;
        this.updatedAt = publishedAt;
    }

    public static CourseTemplateVersion published(
        UUID id,
        UUID courseTemplateId,
        int version,
        Map<String, Object> difficultyProfile,
        Instant publishedAt
    ) {
        return new CourseTemplateVersion(
            id,
            courseTemplateId,
            version,
            difficultyProfile,
            publishedAt
        );
    }

    public UUID getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public Map<String, Object> getDifficultyProfile() {
        return Map.copyOf(difficultyProfile);
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
