package com.b201.ecovillage.identity.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
import com.b201.ecovillage.identity.security.EcoVillagePrincipal;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:auth-bootstrap;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class AuthBootstrapApiTest {

    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");
    private static final String TEACHER_SUBJECT = "bootstrap-teacher";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomTeacherRepository classroomTeacherRepository;

    @Autowired
    private ClassroomStudentRepository classroomStudentRepository;

    private UUID teacherId;

    @BeforeEach
    void setUp() {
        teacherId = UUID.randomUUID();
        userAccountRepository.save(
            UserAccount.createTeacher(
                teacherId,
                TEACHER_SUBJECT,
                "담임 교사",
                NOW
            )
        );
    }

    @Test
    void teacherRestoresAssignedClassroomsAndRoles() throws Exception {
        Classroom ownerClassroom = classroom("AAAA1001", "비빔");
        Classroom cotClassroom = classroom("BBBB1002", "에코");
        assign(ownerClassroom, ClassroomTeacherRole.OWNER);
        assign(cotClassroom, ClassroomTeacherRole.COT);

        mockMvc.perform(get("/auth/bootstrap").principal(teacherPrincipal()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user_id").value(teacherId.toString()))
            .andExpect(jsonPath("$.data.roles[0]").value("OWN"))
            .andExpect(jsonPath("$.data.roles[1]").value("COT"))
            .andExpect(jsonPath("$.data.classrooms.length()").value(2))
            .andExpect(jsonPath("$.data.classrooms[0].classroom_code").value("AAAA1001"))
            .andExpect(jsonPath("$.data.classrooms[0].currency_name").value("비빔"))
            .andExpect(jsonPath("$.data.classrooms[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.classrooms[0].state_version").value(0))
            .andExpect(jsonPath("$.data.start_screen").value("SCR-WEB-002"));
    }

    @Test
    void teacherWithoutClassroomStartsAtCreationWizard() throws Exception {
        mockMvc.perform(get("/auth/bootstrap").principal(teacherPrincipal()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roles[0]").value("TCH"))
            .andExpect(jsonPath("$.data.classrooms.length()").value(0))
            .andExpect(jsonPath("$.data.start_screen").value("SCR-WEB-003"));
    }

    @Test
    void studentRestoresOnlyOwnClassroom() throws Exception {
        Classroom classroom = classroom("STUD1001", "마을돈");
        ClassroomStudent student = classroomStudentRepository.save(
            ClassroomStudent.enroll(
                UUID.randomUUID(),
                classroom.getId(),
                7,
                "하늘",
                NOW
            )
        );

        mockMvc.perform(get("/auth/bootstrap").principal(studentPrincipal(student)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user_id").value(student.getId().toString()))
            .andExpect(jsonPath("$.data.roles[0]").value("STU"))
            .andExpect(jsonPath("$.data.roles.length()").value(1))
            .andExpect(jsonPath("$.data.classrooms.length()").value(1))
            .andExpect(jsonPath("$.data.classrooms[0].resource_id")
                .value(classroom.getId().toString()))
            .andExpect(jsonPath("$.data.classrooms[0].classroom_code").value("STUD1001"))
            .andExpect(jsonPath("$.data.start_screen").value("SCR-STU-003"));
    }

    @Test
    void rejectsUnauthenticatedBootstrap() throws Exception {
        mockMvc.perform(get("/auth/bootstrap"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    private Classroom classroom(String code, String currencyName) {
        return classroomRepository.save(
            Classroom.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                code,
                currencyName,
                5,
                CourseSize.ONE_MONTH,
                25,
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-31"),
                "Asia/Seoul",
                NOW
            )
        );
    }

    private void assign(Classroom classroom, ClassroomTeacherRole role) {
        classroomTeacherRepository.save(
            ClassroomTeacher.assign(
                UUID.randomUUID(),
                classroom.getId(),
                teacherId,
                role,
                NOW
            )
        );
    }

    private EcoVillagePrincipal teacherPrincipal() {
        return new EcoVillagePrincipal(
            TEACHER_SUBJECT,
            UUID.randomUUID(),
            teacherId,
            "TCH",
            null,
            NOW.plusSeconds(900)
        );
    }

    private static EcoVillagePrincipal studentPrincipal(ClassroomStudent student) {
        return new EcoVillagePrincipal(
            student.getId().toString(),
            UUID.randomUUID(),
            student.getId(),
            "STU",
            student.getClassroomId(),
            NOW.plusSeconds(900)
        );
    }
}
