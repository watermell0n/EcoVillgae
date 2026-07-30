package com.b201.ecovillage.course.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.b201.ecovillage.identity.domain.CourseSize;

@Entity
@Table(name = "course_templates")
public class CourseTemplate {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "template_code", nullable = false, updatable = false, length = 40)
    private String templateCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "grade_min", nullable = false, updatable = false)
    private int gradeMin;

    @Column(name = "grade_max", nullable = false, updatable = false)
    private int gradeMax;

    @Enumerated(EnumType.STRING)
    @Column(name = "scale_label", nullable = false, updatable = false, length = 20)
    private CourseSize courseSize;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourseTemplate() {
    }

    private CourseTemplate(
        UUID id,
        String templateCode,
        String name,
        int grade,
        CourseSize courseSize,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.templateCode = required(templateCode, 40, "templateCode");
        this.name = required(name, 120, "name");
        if (grade < 4 || grade > 6) {
            throw new IllegalArgumentException("grade must be between 4 and 6");
        }
        this.gradeMin = grade;
        this.gradeMax = grade;
        this.courseSize = Objects.requireNonNull(courseSize, "courseSize must not be null");
        this.status = "PUBLISHED";
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = createdAt;
    }

    public static CourseTemplate published(
        UUID id,
        String templateCode,
        String name,
        int grade,
        CourseSize courseSize,
        Instant createdAt
    ) {
        return new CourseTemplate(
            id,
            templateCode,
            name,
            grade,
            courseSize,
            createdAt
        );
    }

    public UUID getId() {
        return id;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public String getName() {
        return name;
    }

    public int getGradeMin() {
        return gradeMin;
    }

    public int getGradeMax() {
        return gradeMax;
    }

    public CourseSize getCourseSize() {
        return courseSize;
    }

    private static String required(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return normalized;
    }
}
