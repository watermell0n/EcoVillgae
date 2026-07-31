package com.b201.ecovillage.identity.domain;

import java.time.Instant;
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
@Table(name = "device_links")
public class DeviceLink {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "classroom_id", nullable = false, updatable = false)
    private UUID classroomId;

    @Column(name = "classroom_student_id", nullable = false, updatable = false)
    private UUID classroomStudentId;

    @Column(name = "device_fingerprint_hash", nullable = false, updatable = false, length = 128)
    private String deviceFingerprintHash;

    @Column(name = "device_name", length = 120)
    private String deviceName;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DeviceLinkStatus status;

    @Version
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeviceLink() {
    }

    private DeviceLink(
        UUID id,
        UUID classroomId,
        UUID classroomStudentId,
        String deviceFingerprintHash,
        Instant linkedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.classroomStudentId = Objects.requireNonNull(
            classroomStudentId,
            "classroomStudentId must not be null"
        );
        this.deviceFingerprintHash = requireText(
            deviceFingerprintHash,
            "deviceFingerprintHash"
        );
        this.linkedAt = Objects.requireNonNull(linkedAt, "linkedAt must not be null");
        this.lastSeenAt = linkedAt;
        this.status = DeviceLinkStatus.ACTIVE;
        this.stateVersion = 0;
        this.statusChangedAt = linkedAt;
        this.createdAt = linkedAt;
        this.updatedAt = linkedAt;
    }

    public static DeviceLink link(
        UUID id,
        UUID classroomId,
        UUID classroomStudentId,
        String deviceFingerprintHash,
        Instant linkedAt
    ) {
        return new DeviceLink(
            id,
            classroomId,
            classroomStudentId,
            deviceFingerprintHash,
            linkedAt
        );
    }

    public void activate(Instant seenAt) {
        Instant changedAt = Objects.requireNonNull(seenAt, "seenAt must not be null");
        this.lastSeenAt = changedAt;
        this.revokedAt = null;
        this.status = DeviceLinkStatus.ACTIVE;
        this.statusChangedAt = changedAt;
        this.updatedAt = changedAt;
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

    public String getDeviceFingerprintHash() {
        return deviceFingerprintHash;
    }

    public DeviceLinkStatus getStatus() {
        return status;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
