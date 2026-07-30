package com.b201.ecovillage.identity.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomTeacher;
import com.b201.ecovillage.identity.domain.ClassroomTeacherRole;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.ClassroomTeacherRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:classroom-authorization;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class ClassroomAuthorizationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    @Autowired
    private ClassroomAuthorizationService authorizationService;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomTeacherRepository classroomTeacherRepository;

    @Autowired
    private ClassroomStudentRepository classroomStudentRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    private UUID classroomA;
    private UUID classroomB;
    private UUID studentA;
    private UUID studentB;

    @BeforeEach
    void setUp() {
        classroomA = UUID.randomUUID();
        classroomB = UUID.randomUUID();
        studentA = UUID.randomUUID();
        studentB = UUID.randomUUID();

        UUID ownerA = teacher("owner-a");
        UUID cotA = teacher("cot-a");
        UUID ownerB = teacher("owner-b");
        classroomRepository.save(classroom(classroomA, "ABCD2345"));
        classroomRepository.save(classroom(classroomB, "EFGH6789"));
        classroomTeacherRepository.save(
            ClassroomTeacher.assign(
                UUID.randomUUID(),
                classroomA,
                ownerA,
                ClassroomTeacherRole.OWNER,
                NOW
            )
        );
        classroomTeacherRepository.save(
            ClassroomTeacher.assign(
                UUID.randomUUID(),
                classroomA,
                cotA,
                ClassroomTeacherRole.COT,
                NOW
            )
        );
        classroomTeacherRepository.save(
            ClassroomTeacher.assign(
                UUID.randomUUID(),
                classroomB,
                ownerB,
                ClassroomTeacherRole.OWNER,
                NOW
            )
        );
        classroomStudentRepository.save(
            ClassroomStudent.enroll(studentA, classroomA, 1, "하늘", NOW)
        );
        classroomStudentRepository.save(
            ClassroomStudent.enroll(studentB, classroomB, 1, "바다", NOW)
        );
    }

    @Test
    void allowsOwnerToManageOwnedClassroom() {
        AuthorizationDecision decision = authorizationService.authorize(
            "owner-a",
            classroomA,
            ClassroomPermission.MANAGE_ROSTER,
            null
        );

        assertEquals("ALLOW", decision.decision());
        assertEquals("PM-CLS-004", decision.ruleId());
        assertEquals(ClassroomActorRole.OWN, decision.actorType());
        assertEquals(AuthorizationScope.OWNED_CLASS, decision.scope());
        assertEquals(classroomA, decision.resourceId());
    }

    @Test
    void allowsCoTeacherReadButDeniesOwnerOnlyMutation() {
        AuthorizationDecision readDecision = authorizationService.authorize(
            "cot-a",
            classroomA,
            ClassroomPermission.READ_CONFIGURATION,
            null
        );
        assertEquals(ClassroomActorRole.COT, readDecision.actorType());
        assertEquals(AuthorizationScope.ASSIGNED_CLASS, readDecision.scope());

        ClassroomOperationException exception = assertThrows(
            ClassroomOperationException.class,
            () -> authorizationService.authorize(
                "cot-a",
                classroomA,
                ClassroomPermission.MANAGE_ROSTER,
                null
            )
        );
        assertEquals("OWNER_REQUIRED", exception.getCode());
    }

    @Test
    void blocksTeacherFromOtherClassroom() {
        ClassroomOperationException exception = assertThrows(
            ClassroomOperationException.class,
            () -> authorizationService.authorize(
                "owner-a",
                classroomB,
                ClassroomPermission.READ_CONFIGURATION,
                null
            )
        );

        assertEquals("CLASS_NOT_ASSIGNED", exception.getCode());
    }

    @Test
    void allowsStudentOnlyForOwnResourceInOwnClassroom() {
        AuthorizationDecision decision = authorizationService.authorize(
            studentA.toString(),
            classroomA,
            ClassroomPermission.READ_OWN_STUDENT_DATA,
            studentA
        );
        assertEquals(ClassroomActorRole.STU, decision.actorType());
        assertEquals(AuthorizationScope.SELF, decision.scope());

        ClassroomOperationException otherResource = assertThrows(
            ClassroomOperationException.class,
            () -> authorizationService.authorize(
                studentA.toString(),
                classroomA,
                ClassroomPermission.READ_OWN_STUDENT_DATA,
                studentB
            )
        );
        assertEquals("RESOURCE_NOT_OWNED", otherResource.getCode());
    }

    @Test
    void blocksStudentFromOtherClassroomAndTeacherOnlyPermission() {
        ClassroomOperationException otherClassroom = assertThrows(
            ClassroomOperationException.class,
            () -> authorizationService.authorize(
                studentA.toString(),
                classroomB,
                ClassroomPermission.READ_OWN_STUDENT_DATA,
                studentA
            )
        );
        assertEquals("CLASS_NOT_ASSIGNED", otherClassroom.getCode());

        ClassroomOperationException teacherOnly = assertThrows(
            ClassroomOperationException.class,
            () -> authorizationService.authorize(
                studentA.toString(),
                classroomA,
                ClassroomPermission.READ_CONFIGURATION,
                null
            )
        );
        assertEquals("ROLE_NOT_ALLOWED", teacherOnly.getCode());
    }

    @Test
    void rejectsMissingAuthentication() {
        ClassroomOperationException exception = assertThrows(
            ClassroomOperationException.class,
            () -> authorizationService.authorize(
                null,
                classroomA,
                ClassroomPermission.READ_CONFIGURATION,
                null
            )
        );
        assertEquals("AUTH_REQUIRED", exception.getCode());
    }

    private UUID teacher(String subject) {
        UUID id = UUID.randomUUID();
        userAccountRepository.save(
            UserAccount.createTeacher(id, subject, subject, NOW)
        );
        return id;
    }

    private Classroom classroom(UUID id, String code) {
        return Classroom.create(
            id,
            UUID.randomUUID(),
            code,
            "에코",
            5,
            CourseSize.ONE_MONTH,
            20,
            LocalDate.parse("2026-08-01"),
            LocalDate.parse("2026-08-31"),
            "Asia/Seoul",
            NOW
        );
    }
}
