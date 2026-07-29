package com.b201.ecovillage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.StudentCredential;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.StudentCredentialRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ecovillage;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class EcoVillageApplicationTest {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private ClassroomStudentRepository classroomStudentRepository;

    @Autowired
    private StudentCredentialRepository studentCredentialRepository;

    @Test
    void applicationContextLoads() {
    }

    @Test
    @Transactional
    void persistsTeacherStudentAndSeparatedCredential() {
        Instant now = Instant.parse("2026-07-29T00:00:00Z");
        UUID classroomId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        userAccountRepository.saveAndFlush(
            UserAccount.createTeacher(UUID.randomUUID(), "teacher-1", "교사", now)
        );
        classroomStudentRepository.saveAndFlush(
            ClassroomStudent.enroll(studentId, classroomId, 7, "경제왕", now)
        );
        studentCredentialRepository.saveAndFlush(
            StudentCredential.issue(studentId, classroomId, "$argon2id$hash", now)
        );

        assertTrue(userAccountRepository.findByExternalSubject("teacher-1").isPresent());
        assertEquals(
            studentId,
            classroomStudentRepository
                .findByClassroomIdAndRosterNumberAndRetiredAtIsNull(classroomId, 7)
                .orElseThrow()
                .getId()
        );
        assertEquals(
            "$argon2id$hash",
            studentCredentialRepository
                .findByClassroomIdAndClassroomStudentId(classroomId, studentId)
                .orElseThrow()
                .getPasswordHash()
        );
    }
}
