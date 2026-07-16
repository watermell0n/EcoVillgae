package com.b201.bibim.domain.monitoring;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_presences")
public class StudentPresenceEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "student_id", nullable = false, unique = true)
    private UUID studentId;

    @Column(name = "reported_stage", nullable = false, length = 50)
    private String reportedStage;

    @Column(name = "current_module", length = 40)
    private String currentModule;

    @Column(name = "current_page", nullable = false, length = 80)
    private String currentPage;

    @Column(name = "client_sync_version", nullable = false)
    private long clientSyncVersion;

    @Column(nullable = false, length = 20)
    private String visibility;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "reconnected_at")
    private Instant reconnectedAt;

    @Column(name = "forced_offline_at")
    private Instant forcedOfflineAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudentPresenceEntity() {
    }

    public static StudentPresenceEntity firstSeen(
            UUID roomId,
            UUID studentId,
            String reportedStage,
            String currentModule,
            String currentPage,
            long clientSyncVersion,
            String visibility,
            Instant now
    ) {
        StudentPresenceEntity presence = new StudentPresenceEntity();
        presence.id = UUID.randomUUID();
        presence.roomId = roomId;
        presence.studentId = studentId;
        presence.firstSeenAt = now;
        presence.update(
                reportedStage,
                currentModule,
                currentPage,
                clientSyncVersion,
                visibility,
                now,
                false
        );
        return presence;
    }

    public void update(
            String reportedStage,
            String currentModule,
            String currentPage,
            long clientSyncVersion,
            String visibility,
            Instant now,
            boolean reconnected
    ) {
        this.reportedStage = reportedStage;
        this.currentModule = currentModule;
        this.currentPage = currentPage;
        this.clientSyncVersion = clientSyncVersion;
        this.visibility = visibility;
        this.lastSeenAt = now;
        this.updatedAt = now;
        this.forcedOfflineAt = null;
        if (reconnected) {
            this.reconnectedAt = now;
        }
    }

    public void forceOffline(Instant now) {
        this.forcedOfflineAt = now;
        this.currentPage = "SESSION_TERMINATED";
        this.visibility = "HIDDEN";
        this.updatedAt = now;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public String getReportedStage() {
        return reportedStage;
    }

    public String getCurrentModule() {
        return currentModule;
    }

    public String getCurrentPage() {
        return currentPage;
    }

    public long getClientSyncVersion() {
        return clientSyncVersion;
    }

    public String getVisibility() {
        return visibility;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getReconnectedAt() {
        return reconnectedAt;
    }

    public Instant getForcedOfflineAt() {
        return forcedOfflineAt;
    }
}
