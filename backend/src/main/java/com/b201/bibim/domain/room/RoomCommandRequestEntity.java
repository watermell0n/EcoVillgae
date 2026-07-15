package com.b201.bibim.domain.room;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "room_command_requests")
public class RoomCommandRequestEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, length = 40)
    private String command;

    @Column(name = "target_stage", nullable = false, length = 50)
    private String targetStage;

    @Column(name = "expected_version", nullable = false)
    private int expectedVersion;

    @Column(name = "resulting_version", nullable = false)
    private int resultingVersion;

    @Column(name = "resulting_sync_version", nullable = false)
    private long resultingSyncVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RoomCommandRequestEntity() {
    }

    private RoomCommandRequestEntity(
            UUID roomId,
            String idempotencyKey,
            String command,
            String targetStage,
            int expectedVersion,
            int resultingVersion,
            long resultingSyncVersion
    ) {
        this.id = UUID.randomUUID();
        this.roomId = roomId;
        this.idempotencyKey = idempotencyKey;
        this.command = command;
        this.targetStage = targetStage;
        this.expectedVersion = expectedVersion;
        this.resultingVersion = resultingVersion;
        this.resultingSyncVersion = resultingSyncVersion;
        this.createdAt = Instant.now();
    }

    public static RoomCommandRequestEntity completed(
            RoomEntity room,
            String idempotencyKey,
            String command,
            String targetStage,
            int expectedVersion
    ) {
        return new RoomCommandRequestEntity(
                room.getId(),
                idempotencyKey,
                command,
                targetStage,
                expectedVersion,
                room.getVersion(),
                room.getSyncVersion()
        );
    }

    public boolean matches(String requestedCommand, String requestedStage, int requestedVersion) {
        return command.equals(requestedCommand)
                && targetStage.equals(requestedStage)
                && expectedVersion == requestedVersion;
    }
}
