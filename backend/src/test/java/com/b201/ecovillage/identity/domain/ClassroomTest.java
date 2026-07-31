package com.b201.ecovillage.identity.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ClassroomTest {

    private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

    @Test
    void allowsActiveClosingAndLimitedReadOnlyStudentAccess() {
        Classroom classroom = Classroom.active(UUID.randomUUID(), "ABCD2345", NOW);
        assertTrue(classroom.allowsStudentLogin(NOW));

        classroom.changeStatus(ClassroomStatus.CLOSING, NOW);
        assertTrue(classroom.allowsStudentLogin(NOW.plus(1, ChronoUnit.DAYS)));

        classroom.changeStatus(ClassroomStatus.READ_ONLY, NOW);
        assertTrue(classroom.allowsStudentLogin(NOW.plus(29, ChronoUnit.DAYS)));
        assertFalse(classroom.allowsStudentLogin(NOW.plus(30, ChronoUnit.DAYS)));
    }

    @Test
    void rejectsArchivedStudentAccess() {
        Classroom classroom = Classroom.active(UUID.randomUUID(), "ABCD2345", NOW);
        classroom.changeStatus(ClassroomStatus.ARCHIVED, NOW);

        assertFalse(classroom.allowsStudentLogin(NOW));
    }
}
