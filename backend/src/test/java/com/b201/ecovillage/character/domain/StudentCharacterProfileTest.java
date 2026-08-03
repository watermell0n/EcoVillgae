package com.b201.ecovillage.character.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class StudentCharacterProfileTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-02T00:00:00Z");

    @Test
    void startsWithoutEquipmentAndRecordsTheEquipmentAuditTime() {
        StudentCharacterProfile profile = StudentCharacterProfile.createDefault(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            CREATED_AT
        );

        assertEquals(CREATED_AT, profile.getEquipmentChangedAt());
        assertEquals(CREATED_AT, profile.getCreatedAt());
        assertEquals(CREATED_AT, profile.getUpdatedAt());
        assertEquals(0, profile.getEquipmentRevision());

        profile.markEquipmentChanged(CREATED_AT.plusSeconds(2));

        assertEquals(CREATED_AT.plusSeconds(2), profile.getEquipmentChangedAt());
        assertEquals(1, profile.getEquipmentRevision());
        assertEquals(CREATED_AT.plusSeconds(2), profile.getUpdatedAt());
    }
}
