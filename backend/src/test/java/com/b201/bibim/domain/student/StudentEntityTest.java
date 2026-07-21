package com.b201.bibim.domain.student;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class StudentEntityTest {

    @Test
    void keepsTheFirstAssignedRoleName() {
        StudentEntity student = StudentEntity.invited(UUID.randomUUID(), 1, "ECO-01");

        student.assignRoleName(" 라이츄 ");

        assertEquals("라이츄", student.getRoleName());
        assertThrows(IllegalStateException.class, () -> student.assignRoleName("미술 부장"));
        assertEquals("라이츄", student.getRoleName());
    }

    @Test
    void passwordResetKeepsNicknameAndActiveStatus() {
        StudentEntity student = StudentEntity.invited(UUID.randomUUID(), 1, "ECO-01");
        student.completeSetup("비빔이", "encoded-password");

        student.resetPassword();

        assertEquals("비빔이", student.getNickname());
        assertEquals("ACTIVE", student.getStatus());
        assertNull(student.getPasswordHash());
    }
}
