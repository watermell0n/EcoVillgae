package com.b201.bibim.domain.room;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sessions")
public class DaySessionEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "day_number", nullable = false)
    private short dayNumber;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "current_stage", nullable = false, length = 50)
    private String currentStage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected DaySessionEntity() {
    }

    public static DaySessionEntity dayOneReady(UUID roomId) {
        DaySessionEntity session = new DaySessionEntity();
        session.id = UUID.randomUUID();
        session.roomId = roomId;
        session.dayNumber = 1;
        session.status = "READY";
        session.currentStage = RoomStage.DAY1_LOBBY.name();
        return session;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public int getDayNumber() {
        return dayNumber;
    }
}
