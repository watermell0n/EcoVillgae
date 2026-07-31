package com.b201.ecovillage.identity.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ClassroomStudentTest {

    private static final Instant JOINED_AT = Instant.parse("2026-07-29T00:00:00Z");

    @Test
    void enrollsStudentWithRosterNumberAndNicknameOnly() {
        ClassroomStudent student = ClassroomStudent.enroll(
            UUID.randomUUID(),
            UUID.randomUUID(),
            12,
            " 경제왕 ",
            JOINED_AT
        );

        assertEquals(12, student.getRosterNumber());
        assertEquals("경제왕", student.getNickname());
        assertEquals(ClassroomStudentStatus.ACTIVE, student.getStatus());
        assertNull(student.getRetiredAt());
    }

    @Test
    void rejectsInvalidRosterNumberAndNickname() {
        UUID id = UUID.randomUUID();
        UUID classroomId = UUID.randomUUID();

        assertThrows(
            IllegalArgumentException.class,
            () -> ClassroomStudent.enroll(id, classroomId, 0, "학생", JOINED_AT)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> ClassroomStudent.enroll(id, classroomId, 1, " ", JOINED_AT)
        );
    }

    @Test
    void retiresStudentWithoutDeletingAccountRecord() {
        ClassroomStudent student = ClassroomStudent.enroll(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            "학생",
            JOINED_AT
        );
        Instant retiredAt = JOINED_AT.plusSeconds(60);

        student.retire(retiredAt);

        assertEquals(ClassroomStudentStatus.RETIRED, student.getStatus());
        assertEquals(retiredAt, student.getRetiredAt());
    }
}
