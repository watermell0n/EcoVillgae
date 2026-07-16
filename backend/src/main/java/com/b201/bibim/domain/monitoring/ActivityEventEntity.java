package com.b201.bibim.domain.monitoring;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "activity_events")
public class ActivityEventEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "actor_type", nullable = false, length = 20)
    private String actorType;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(length = 50)
    private String stage;

    @Column(length = 40)
    private String module;

    @Column(length = 80)
    private String page;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ActivityEventEntity() {
    }

    public static ActivityEventEntity create(
            UUID roomId,
            UUID studentId,
            String actorType,
            UUID actorId,
            String eventType,
            String stage,
            String module,
            String page,
            String requestId,
            Map<String, Object> metadata
    ) {
        ActivityEventEntity event = new ActivityEventEntity();
        event.id = UUID.randomUUID();
        event.roomId = roomId;
        event.studentId = studentId;
        event.actorType = actorType;
        event.actorId = actorId;
        event.eventType = eventType;
        event.stage = stage;
        event.module = module;
        event.page = page;
        event.requestId = requestId;
        event.metadata = Map.copyOf(metadata);
        event.occurredAt = Instant.now();
        return event;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public String getActorType() {
        return actorType;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getStage() {
        return stage;
    }

    public String getModule() {
        return module;
    }

    public String getPage() {
        return page;
    }

    public String getRequestId() {
        return requestId;
    }

    public Map<String, Object> getMetadata() {
        return Map.copyOf(metadata);
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
