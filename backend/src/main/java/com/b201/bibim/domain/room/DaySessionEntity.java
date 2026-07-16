package com.b201.bibim.domain.room;

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

    protected DaySessionEntity() {
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
