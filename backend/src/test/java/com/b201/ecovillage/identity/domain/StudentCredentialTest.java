package com.b201.ecovillage.identity.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class StudentCredentialTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-07-29T00:00:00Z");

    @Test
    void storesPasswordHashSeparatelyFromStudentProfile() {
        UUID studentId = UUID.randomUUID();
        UUID classroomId = UUID.randomUUID();

        StudentCredential credential = StudentCredential.issue(
            studentId,
            classroomId,
            "$argon2id$hash",
            ISSUED_AT
        );

        assertEquals(studentId, credential.getClassroomStudentId());
        assertEquals(classroomId, credential.getClassroomId());
        assertEquals("$argon2id$hash", credential.getPasswordHash());
        assertEquals(1, credential.getCredentialVersion());
    }

    @Test
    void rejectsBlankPasswordHash() {
        assertThrows(
            IllegalArgumentException.class,
            () -> StudentCredential.issue(
                UUID.randomUUID(),
                UUID.randomUUID(),
                " ",
                ISSUED_AT
            )
        );
    }
}
