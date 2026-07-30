package com.b201.bibim.domain.character;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "student_characters")
public class StudentCharacterEntity {

    public static final String DEFAULT_NAME = "이코";

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(nullable = false, length = 50)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> equipped;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StudentCharacterEntity() {
    }

    public static StudentCharacterEntity create(UUID roomId, UUID studentId) {
        StudentCharacterEntity character = new StudentCharacterEntity();
        character.id = UUID.randomUUID();
        character.roomId = roomId;
        character.studentId = studentId;
        character.name = DEFAULT_NAME;
        character.equipped = new HashMap<>();
        character.createdAt = Instant.now();
        return character;
    }

    /**
     * 슬롯당 1개만 착용한다. cosmeticId 가 null 이면 해당 슬롯을 비운다(무료·원장 미기록).
     */
    public void equip(String slot, UUID cosmeticId) {
        Map<String, Object> next = new HashMap<>(equipped == null ? Map.of() : equipped);
        if (cosmeticId == null) {
            next.remove(slot);
        } else {
            next.put(slot, cosmeticId.toString());
        }
        this.equipped = next;
    }

    public void rename(String nextName) {
        this.name = nextName;
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

    public String getName() {
        return name;
    }

    public Map<String, Object> getEquipped() {
        return equipped == null ? Map.of() : Map.copyOf(equipped);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
