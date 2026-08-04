package com.b201.ecovillage.character.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
    name = "student_character_profiles",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_student_character_profiles_student",
        columnNames = {"classroom_id", "classroom_student_id"}
    )
)
public class StudentCharacterProfile {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "classroom_student_id", nullable = false, updatable = false)
    private UUID classroomStudentId;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "equipment_revision", nullable = false)
    private long equipmentRevision;

    @Column(name = "equipment_changed_at", nullable = false)
    private Instant equipmentChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudentCharacterProfile() {
    }

    private StudentCharacterProfile(
        UUID id,
        UUID classroomId,
        UUID classroomStudentId,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.classroomStudentId = Objects.requireNonNull(
            classroomStudentId,
            "classroomStudentId must not be null"
        );
        Instant now = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.equipmentChangedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static StudentCharacterProfile createDefault(
        UUID id,
        UUID classroomId,
        UUID classroomStudentId,
        Instant createdAt
    ) {
        return new StudentCharacterProfile(
            id,
            classroomId,
            classroomStudentId,
            createdAt
        );
    }

    public void markEquipmentChanged(Instant changedAt) {
        Instant now = Objects.requireNonNull(changedAt, "changedAt must not be null");
        equipmentRevision += 1;
        equipmentChangedAt = now;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public UUID getClassroomStudentId() {
        return classroomStudentId;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    public long getEquipmentRevision() {
        return equipmentRevision;
    }

    public Instant getEquipmentChangedAt() {
        return equipmentChangedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
