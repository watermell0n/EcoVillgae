package com.b201.ecovillage.classroom.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.repository.AutonomousScheduleModuleRepository;
import com.b201.ecovillage.classroom.repository.AutonomousScheduleRepository;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.identity.domain.SchoolMembership;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomTeacherRepository;
import com.b201.ecovillage.identity.repository.SchoolMembershipRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:classroom-creation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class ClassroomCreationApiTest {

    private static final String TEACHER_SUBJECT = "teacher-122";
    private static final Instant NOW = Instant.parse("2026-07-30T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private SchoolMembershipRepository schoolMembershipRepository;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomTeacherRepository classroomTeacherRepository;

    @Autowired
    private ClassroomModuleRepository classroomModuleRepository;

    @Autowired
    private ClassroomPolicyVersionRepository policyVersionRepository;

    @Autowired
    private AutonomousScheduleRepository scheduleRepository;

    @Autowired
    private AutonomousScheduleModuleRepository scheduleModuleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID schoolId;
    private Principal principal;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        principal = () -> TEACHER_SUBJECT;
        userAccountRepository.save(
            UserAccount.createTeacher(teacherId, TEACHER_SUBJECT, "담임 교사", NOW)
        );
        schoolMembershipRepository.save(
            SchoolMembership.assignTeacher(
                UUID.randomUUID(),
                schoolId,
                teacherId,
                NOW
            )
        );
    }

    @Test
    void createsClassroomOwnerAndDisabledSelectedModulesWithoutDefaultPolicy() throws Exception {
        String response = mockMvc.perform(
            post("/classrooms")
                .principal(principal)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreationBody("에코", "ONE_WEEK"))
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.classroom_code").value(
                org.hamcrest.Matchers.matchesPattern(
                    "^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}$"
                )
            ))
            .andExpect(jsonPath("$.data.currency_name").value("에코"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.state_version").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String classroomId = com.jayway.jsonpath.JsonPath.read(
            response,
            "$.data.classroom_id"
        );
        UUID id = UUID.fromString(classroomId);
        assertEquals(1, classroomRepository.count());
        assertEquals(1, classroomTeacherRepository.count());
        assertEquals(2, classroomModuleRepository.count());
        assertEquals(0, policyVersionRepository.count());
        assertEquals(1, scheduleRepository.count());
        assertEquals(2, scheduleModuleRepository.count());
        assertEquals(0L, jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ledger_accounts",
            Long.class
        ));
        assertTrue(classroomModuleRepository.findAll().stream().allMatch(
            module -> "DISABLED".equals(module.getStatus())
                && module.getEnabledAt() == null
        ));

        mockMvc.perform(
            get("/classrooms/{classroomId}/configuration", id)
                .principal(principal)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.classroom_code").isString())
            .andExpect(jsonPath("$.data.currency_name").value("에코"))
            .andExpect(jsonPath("$.data.module_codes.length()").value(2))
            .andExpect(jsonPath("$.data.autonomous_schedules.length()").value(1))
            .andExpect(jsonPath("$.data.policy_versions.length()").value(0));
    }

    @Test
    void createsClassroomWithoutAnyOptionalModules() throws Exception {
        String body = """
            {
              "school_id": "%s",
              "currency_name": "ECO",
              "grade": 5,
              "course_size": "ONE_MONTH",
              "starts_on": "2026-08-01",
              "ends_on": "2026-08-31",
              "student_count": 24,
              "timezone": "Asia/Seoul",
              "module_codes": []
            }
            """.formatted(schoolId);

        mockMvc.perform(
            post("/classrooms")
                .principal(principal)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isCreated());

        assertEquals(0, classroomModuleRepository.count());
        assertEquals(0, policyVersionRepository.count());
        assertEquals(0L, jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ledger_accounts",
            Long.class
        ));
    }

    @Test
    void rejectsForbiddenCurrencyNameWithoutPartialRows() throws Exception {
        mockMvc.perform(
            post("/classrooms")
                .principal(principal)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreationBody("관리자돈", "ONE_WEEK"))
        )
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("INVALID_CURRENCY_NAME"));

        assertEquals(0, classroomRepository.count());
        assertEquals(0, classroomTeacherRepository.count());
        assertEquals(0, classroomModuleRepository.count());
    }

    @Test
    void restrictsOneSessionToQuizModules() throws Exception {
        mockMvc.perform(
            post("/classrooms")
                .principal(principal)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreationBody("에코", "ONE_SESSION"))
        )
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("POLICY_VIOLATION"));

        assertTrue(classroomRepository.findAll().isEmpty());
    }

    @Test
    void rejectsTeacherOutsideRequestedSchool() throws Exception {
        String body = validCreationBody("에코", "ONE_WEEK")
            .replace(schoolId.toString(), UUID.randomUUID().toString());

        mockMvc.perform(
            post("/classrooms")
                .principal(principal)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("SCHOOL_SCOPE_VIOLATION"));
    }

    @Test
    void requiresAuthenticatedTeacherPrincipal() throws Exception {
        mockMvc.perform(
            post("/classrooms")
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreationBody("에코", "ONE_WEEK"))
        )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    private String validCreationBody(String currencyName, String courseSize) {
        return """
            {
              "school_id": "%s",
              "currency_name": "%s",
              "grade": 5,
              "course_size": "%s",
              "starts_on": "2026-08-01",
              "ends_on": "2026-08-31",
              "student_count": 24,
              "timezone": "Asia/Seoul",
              "salary_policy": {},
              "module_codes": ["STORE", "QUIZ"],
              "autonomous_schedules": [{
                "day_of_week": 1,
                "local_start_time": "12:10:00",
                "local_end_time": "12:50:00",
                "module_codes": ["STORE", "QUIZ"]
              }]
            }
            """.formatted(schoolId, currencyName, courseSize);
    }
}
