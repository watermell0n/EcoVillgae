package com.b201.ecovillage.identity.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class UserAccountTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-29T00:00:00Z");

    @Test
    void createsActiveTeacherWithoutStudentRealNameFields() {
        UserAccount teacher = UserAccount.createTeacher(
            UUID.randomUUID(),
            "oidc|teacher-1",
            "담임 교사",
            CREATED_AT
        );

        assertEquals(UserType.TEACHER, teacher.getUserType());
        assertEquals(AccountStatus.ACTIVE, teacher.getStatus());
        assertEquals("oidc|teacher-1", teacher.getExternalSubject());
        assertEquals("담임 교사", teacher.getDisplayName());
    }

    @Test
    void rejectsBlankExternalSubjectAndDisplayName() {
        UUID id = UUID.randomUUID();

        assertThrows(
            IllegalArgumentException.class,
            () -> UserAccount.createTeacher(id, " ", "교사", CREATED_AT)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> UserAccount.createTeacher(id, "teacher-1", " ", CREATED_AT)
        );
    }

    @Test
    void deactivatesTeacherAccount() {
        UserAccount teacher = UserAccount.createTeacher(
            UUID.randomUUID(),
            "teacher-1",
            "교사",
            CREATED_AT
        );

        teacher.deactivate(CREATED_AT.plusSeconds(60));

        assertEquals(AccountStatus.INACTIVE, teacher.getStatus());
    }
}
