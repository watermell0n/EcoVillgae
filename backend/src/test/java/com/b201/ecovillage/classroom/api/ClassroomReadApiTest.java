package com.b201.ecovillage.classroom.api;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.domain.SalaryRole;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.classroom.repository.SalaryRoleRepository;
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
    "spring.datasource.url=jdbc:h2:mem:classroom-read;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class ClassroomReadApiTest {

    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");
    private static final String OWNER_SUBJECT = "read-owner";
    private static final String COT_SUBJECT = "read-cot";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomTeacherRepository classroomTeacherRepository;

    @Autowired
    private ClassroomStudentRepository studentRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private SalaryRoleRepository salaryRoleRepository;

    @Autowired
    private ClassroomModuleRepository moduleRepository;

    @Autowired
    private ClassroomPolicyVersionRepository policyVersionRepository;

    private UUID classroomId;
    private ClassroomStudent student;
    private ClassroomModule salaryModule;

    @BeforeEach
    void setUp() {
        classroomId = UUID.randomUUID();
        Classroom classroom = classroomRepository.save(
            Classroom.create(
                classroomId,
                UUID.randomUUID(),
                "READ1001",
                "비빔",
                5,
                CourseSize.ONE_MONTH,
                25,
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-31"),
                "Asia/Seoul",
                NOW
            )
        );
        assignTeacher(classroom, OWNER_SUBJECT, ClassroomTeacherRole.OWNER);
        assignTeacher(classroom, COT_SUBJECT, ClassroomTeacherRole.COT);

        SalaryRole role = salaryRoleRepository.save(
            SalaryRole.create(
                UUID.randomUUID(),
                classroomId,
                "LEADER",
                "반장",
                1200,
                Map.of("tax_rate_bps", 1000),
                1,
                NOW
            )
        );
        student = ClassroomStudent.enroll(
            UUID.randomUUID(),
            classroomId,
            1,
            "하늘",
            NOW
        );
        student.assignSalaryRole(role.getId(), NOW);
        studentRepository.save(student);

        salaryModule = ClassroomModule.disabled(
            UUID.randomUUID(),
            classroomId,
            "ROLE_SALARY",
            NOW
        );
        salaryModule.activate(
            Map.of(
                "rule_version", "2026-08-01",
                "recommendation_action_ids", List.of(UUID.randomUUID().toString()),
                "policy_area", "SALARY",
                "policy_version", 1
            ),
            NOW
        );
        moduleRepository.save(salaryModule);
        moduleRepository.save(
            ClassroomModule.disabled(
                UUID.randomUUID(),
                classroomId,
                "STORE",
                NOW
            )
        );
        policyVersionRepository.save(
            ClassroomPolicyVersion.salary(
                UUID.randomUUID(),
                classroomId,
                1,
                Map.of("roles", List.of()),
                "TEACHER",
                NOW
            )
        );
        policyVersionRepository.save(
            ClassroomPolicyVersion.policy(
                UUID.randomUUID(),
                classroomId,
                "AI_SETTINGS",
                1,
                Map.of("enabled", true),
                "TEACHER",
                NOW
            )
        );
    }

    @Test
    void ownerReadsRosterAndSalaryRoleVersions() throws Exception {
        mockMvc.perform(
            get("/classrooms/{classroomId}/roster-and-salaries", classroomId)
                .principal(() -> OWNER_SUBJECT)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.students.length()").value(1))
            .andExpect(jsonPath("$.data.students[0].resource_id")
                .value(student.getId().toString()))
            .andExpect(jsonPath("$.data.students[0].attendance_number").value(1))
            .andExpect(jsonPath("$.data.students[0].nickname").value("하늘"))
            .andExpect(jsonPath("$.data.students[0].role_code").value("LEADER"))
            .andExpect(jsonPath("$.data.students[0].state_version").isNumber())
            .andExpect(jsonPath("$.data.salary_roles.length()").value(1))
            .andExpect(jsonPath("$.data.salary_roles[0].role_code").value("LEADER"))
            .andExpect(jsonPath("$.data.salary_roles[0].gross_amount").value(1200));
    }

    @Test
    void cotCanReadRoster() throws Exception {
        mockMvc.perform(
            get("/classrooms/{classroomId}/roster-and-salaries", classroomId)
                .principal(() -> COT_SUBJECT)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.students[0].nickname").value("하늘"));
    }

    @Test
    void roleIsNullWhenSalaryModuleIsDisabled() throws Exception {
        salaryModule.disable(NOW.plusSeconds(60));
        moduleRepository.saveAndFlush(salaryModule);

        mockMvc.perform(
            get("/classrooms/{classroomId}/roster-and-salaries", classroomId)
                .principal(() -> OWNER_SUBJECT)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.students[0].role_code").doesNotExist())
            .andExpect(jsonPath("$.data.salary_roles.length()").value(0));
    }

    @Test
    void teacherReadsAllModulesAndPolicyVersions() throws Exception {
        mockMvc.perform(
            get("/classrooms/{classroomId}/modules-and-policies", classroomId)
                .principal(() -> COT_SUBJECT)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.modules.length()").value(2))
            .andExpect(jsonPath("$.data.modules[*].module_code")
                .value(hasItems("ROLE_SALARY", "STORE")))
            .andExpect(jsonPath("$.data.modules[0].config.rule_version")
                .value("2026-08-01"))
            .andExpect(jsonPath("$.data.policy_versions.length()").value(2))
            .andExpect(jsonPath("$.data.policy_versions[*].policy_area")
                .value(hasItems("SALARY", "AI_SETTINGS")));
    }

    @Test
    void studentReadsOnlyActiveModulesAndPublicPolicyMetadata() throws Exception {
        mockMvc.perform(
            get("/classrooms/{classroomId}/modules-and-policies", classroomId)
                .principal(() -> student.getId().toString())
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.modules.length()").value(1))
            .andExpect(jsonPath("$.data.modules[0].module_code").value("ROLE_SALARY"))
            .andExpect(jsonPath("$.data.modules[0].config.policy_area").value("SALARY"))
            .andExpect(jsonPath("$.data.modules[0].config.policy_version").value(1))
            .andExpect(jsonPath("$.data.modules[0].config.rule_version").doesNotExist())
            .andExpect(jsonPath("$.data.modules[0].config.recommendation_action_ids")
                .doesNotExist())
            .andExpect(jsonPath("$.data.policy_versions.length()").value(1))
            .andExpect(jsonPath("$.data.policy_versions[0].policy_area").value("SALARY"));
    }

    @Test
    void rejectsOtherClassroomForStudent() throws Exception {
        Classroom otherClassroom = classroomRepository.save(
            Classroom.active(UUID.randomUUID(), "OTHR1001", NOW)
        );

        mockMvc.perform(
            get(
                "/classrooms/{classroomId}/modules-and-policies",
                otherClassroom.getId()
            ).principal(() -> student.getId().toString())
        )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("CLASS_NOT_ASSIGNED"));
    }

    @Test
    void rejectsUnassignedTeacherAndAnonymousRequest() throws Exception {
        String unassignedSubject = "unassigned-teacher";
        userAccountRepository.save(
            UserAccount.createTeacher(
                UUID.randomUUID(),
                unassignedSubject,
                "미배정 교사",
                NOW
            )
        );

        mockMvc.perform(
            get("/classrooms/{classroomId}/roster-and-salaries", classroomId)
                .principal(() -> unassignedSubject)
        )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("CLASS_NOT_ASSIGNED"));

        mockMvc.perform(
            get("/classrooms/{classroomId}/modules-and-policies", classroomId)
        )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    private void assignTeacher(
        Classroom classroom,
        String subject,
        ClassroomTeacherRole role
    ) {
        UserAccount teacher = userAccountRepository.save(
            UserAccount.createTeacher(
                UUID.randomUUID(),
                subject,
                subject,
                NOW
            )
        );
        classroomTeacherRepository.save(
            ClassroomTeacher.assign(
                UUID.randomUUID(),
                classroom.getId(),
                teacher.getId(),
                role,
                NOW
            )
        );
    }
}
