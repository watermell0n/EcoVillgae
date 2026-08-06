package com.b201.ecovillage.identity.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "classrooms")
public class Classroom {

    private static final Duration STUDENT_READ_ONLY_ACCESS = Duration.ofDays(30);

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_code", nullable = false, updatable = false, length = 8)
    private String classroomCode;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "course_template_version_id")
    private UUID courseTemplateVersionId;

    @Column(name = "currency_name", nullable = false, updatable = false, length = 10)
    private String currencyName;

    @Column(nullable = false)
    private short grade;

    @Column(name = "scale_label", nullable = false, length = 20)
    private String scaleLabel;

    @Column(name = "planned_student_count", nullable = false)
    private int plannedStudentCount;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "class_number")
    private Integer classNumber;

    @Column(length = 500)
    private String description;

    @Column(name = "actual_starts_on")
    private LocalDate actualStartsOn;

    @Column(name = "actual_ends_on")
    private LocalDate actualEndsOn;

    @Column(name = "app_mode_override", length = 40)
    private String appModeOverride;

    @Column(name = "time_zone", nullable = false, length = 60)
    private String timeZone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ClassroomStatus status;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Classroom() {
    }

    private Classroom(
        UUID id,
        UUID schoolId,
        String classroomCode,
        String currencyName,
        int grade,
        CourseSize courseSize,
        int plannedStudentCount,
        LocalDate actualStartsOn,
        LocalDate actualEndsOn,
        String timeZone,
        ClassroomStatus status,
        Instant statusChangedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.schoolId = Objects.requireNonNull(schoolId, "schoolId must not be null");
        this.classroomCode = Objects.requireNonNull(classroomCode, "classroomCode must not be null");
        this.currencyName = Objects.requireNonNull(currencyName, "currencyName must not be null");
        this.grade = (short) grade;
        this.scaleLabel = Objects.requireNonNull(courseSize, "courseSize must not be null").name();
        this.plannedStudentCount = plannedStudentCount;
        this.displayName = grade + "학년 경제교실";
        this.actualStartsOn = actualStartsOn;
        this.actualEndsOn = actualEndsOn;
        this.timeZone = Objects.requireNonNull(timeZone, "timeZone must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.stateVersion = 0;
        this.statusChangedAt = Objects.requireNonNull(
            statusChangedAt,
            "statusChangedAt must not be null"
        );
        this.createdAt = statusChangedAt;
        this.updatedAt = statusChangedAt;
    }

    public static Classroom active(UUID id, String classroomCode, Instant createdAt) {
        return new Classroom(
            id,
            new UUID(0, 0),
            classroomCode,
            "에코",
            4,
            CourseSize.ONE_SESSION,
            1,
            null,
            null,
            "Asia/Seoul",
            ClassroomStatus.ACTIVE,
            createdAt
        );
    }

    public static Classroom create(
        UUID id,
        UUID schoolId,
        String classroomCode,
        String currencyName,
        int grade,
        CourseSize courseSize,
        int plannedStudentCount,
        LocalDate actualStartsOn,
        LocalDate actualEndsOn,
        String timeZone,
        Instant createdAt
    ) {
        return new Classroom(
            id,
            schoolId,
            classroomCode,
            currencyName,
            grade,
            courseSize,
            plannedStudentCount,
            actualStartsOn,
            actualEndsOn,
            timeZone,
            ClassroomStatus.ACTIVE,
            createdAt
        );
    }

    public void changeStatus(ClassroomStatus status, Instant changedAt) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.statusChangedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
        this.updatedAt = changedAt;
    }

    public void touch(Instant changedAt) {
        this.updatedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
    }

    public void updateCourseConfiguration(
        int grade,
        CourseSize courseSize,
        LocalDate startsOn,
        LocalDate endsOn,
        UUID courseTemplateVersionId,
        Instant changedAt
    ) {
        if (grade < 4 || grade > 6) {
            throw new IllegalArgumentException("grade must be between 4 and 6");
        }
        if (startsOn == null || endsOn == null || endsOn.isBefore(startsOn)) {
            throw new IllegalArgumentException("course dates are invalid");
        }
        this.grade = (short) grade;
        this.scaleLabel = Objects.requireNonNull(
            courseSize,
            "courseSize must not be null"
        ).name();
        this.actualStartsOn = startsOn;
        this.actualEndsOn = endsOn;
        this.courseTemplateVersionId = courseTemplateVersionId;
        touch(changedAt);
    }

    public void updateDisplaySettings(
        String displayName,
        Integer classNumber,
        String description,
        Instant changedAt
    ) {
        if (displayName != null) {
            String normalizedName = displayName.trim();
            if (normalizedName.isEmpty() || normalizedName.length() > 120) {
                throw new IllegalArgumentException("displayName must contain 1 to 120 characters");
            }
            this.displayName = normalizedName;
        }
        if (classNumber != null) {
            if (classNumber < 1 || classNumber > 99) {
                throw new IllegalArgumentException("classNumber must be between 1 and 99");
            }
            this.classNumber = classNumber;
        }
        if (description != null) {
            if (description.length() > 500) {
                throw new IllegalArgumentException("description must not exceed 500 characters");
            }
            this.description = description.isBlank() ? null : description.trim();
        }
        touch(changedAt);
    }

    public void setAppModeOverride(String appModeOverride, Instant changedAt) {
        if (
            appModeOverride != null
                && !"AUTONOMOUS".equals(appModeOverride)
                && !"CLOSED".equals(appModeOverride)
        ) {
            throw new IllegalArgumentException(
                "appModeOverride must be AUTONOMOUS, CLOSED, or null"
            );
        }
        this.appModeOverride = appModeOverride;
        touch(changedAt);
    }

    public boolean allowsStudentLogin(Instant now) {
        return switch (status) {
            case ACTIVE, CLOSING -> true;
            case READ_ONLY -> now.isBefore(statusChangedAt.plus(STUDENT_READ_ONLY_ACCESS));
            case ARCHIVED -> false;
        };
    }

    public UUID getId() {
        return id;
    }

    public String getClassroomCode() {
        return classroomCode;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public UUID getCourseTemplateVersionId() {
        return courseTemplateVersionId;
    }

    public String getCurrencyName() {
        return currencyName;
    }

    public int getGrade() {
        return grade;
    }

    public CourseSize getCourseSize() {
        return CourseSize.valueOf(scaleLabel);
    }

    public int getPlannedStudentCount() {
        return plannedStudentCount;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Integer getClassNumber() {
        return classNumber;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getActualStartsOn() {
        return actualStartsOn;
    }

    public LocalDate getActualEndsOn() {
        return actualEndsOn;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public String getAppModeOverride() {
        return appModeOverride;
    }

    public ClassroomStatus getStatus() {
        return status;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }
}
