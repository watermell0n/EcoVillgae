package com.b201.ecovillage.classroom.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
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

import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.classroom.repository.SalaryRoleRepository;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStatus;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.domain.ClassroomTeacher;
import com.b201.ecovillage.identity.domain.ClassroomTeacherRole;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.ClassroomTeacherRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:classroom-roster;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class ClassroomRosterApiTest {

    private static final String OWNER_SUBJECT = "teacher-123";
    private static final Instant NOW = Instant.parse("2026-07-30T02:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomTeacherRepository classroomTeacherRepository;

    @Autowired
    private ClassroomStudentRepository studentRepository;

    @Autowired
    private SalaryRoleRepository salaryRoleRepository;

    @Autowired
    private ClassroomPolicyVersionRepository policyVersionRepository;

    @Autowired
    private ClassroomModuleRepository moduleRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID classroomId;
    private Principal ownerPrincipal;

    @BeforeEach
    void setUp() {
        classroomId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        ownerPrincipal = () -> OWNER_SUBJECT;
        userAccountRepository.save(
            UserAccount.createTeacher(teacherId, OWNER_SUBJECT, "담임 교사", NOW)
        );
        classroomRepository.save(
            Classroom.create(
                classroomId,
                UUID.randomUUID(),
                randomClassroomCode(),
                "에코",
                5,
                CourseSize.ONE_MONTH,
                24,
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-31"),
                "Asia/Seoul",
                NOW
            )
        );
        classroomTeacherRepository.save(
            ClassroomTeacher.assign(
                UUID.randomUUID(),
                classroomId,
                teacherId,
                ClassroomTeacherRole.OWNER,
                NOW
            )
        );
        policyVersionRepository.save(
            ClassroomPolicyVersion.salary(
                UUID.randomUUID(),
                classroomId,
                1,
                Map.of(),
                "TEACHER",
                NOW
            )
        );
        ClassroomModule salaryModule = ClassroomModule.disabled(
            UUID.randomUUID(),
            classroomId,
            "ROLE_SALARY",
            NOW
        );
        salaryModule.activate(Map.of(), NOW);
        moduleRepository.save(salaryModule);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ai_runs (
                classroom_id uuid NOT NULL,
                run_type varchar(40) NOT NULL,
                status varchar(40) NOT NULL,
                state_version bigint NOT NULL,
                status_changed_at timestamp with time zone NOT NULL,
                updated_at timestamp with time zone NOT NULL
            )
            """);
    }

    @Test
    void bulkRegistersStudentsAndSalaryRoles() throws Exception {
        jdbcTemplate.update(
            "INSERT INTO ai_runs VALUES (?, 'SETUP_PRECHECK', 'COMPLETED', 0, ?, ?)",
            classroomId,
            NOW,
            NOW
        );
        mockMvc.perform(
            rosterRequest(bulkBody(1, null))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.classroom_id").value(classroomId.toString()))
            .andExpect(jsonPath("$.data.student_count").value(2))
            .andExpect(jsonPath("$.data.salary_role_count").value(2))
            .andExpect(jsonPath("$.data.state_version").value(1));

        assertEquals(
            2,
            studentRepository.countByClassroomIdAndStatus(
                classroomId,
                ClassroomStudentStatus.ACTIVE
            )
        );
        assertEquals(2, salaryRoleRepository.count());
        assertEquals(2, currentPolicyVersion());
        assertEquals("EXPIRED", jdbcTemplate.queryForObject(
            "SELECT status FROM ai_runs WHERE classroom_id = ?",
            String.class,
            classroomId
        ));
    }

    @Test
    void individualRegistrationPreservesExistingStudents() throws Exception {
        mockMvc.perform(rosterRequest(singleStudentBody(1, null, 1, "하늘", 1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.student_count").value(1));

        mockMvc.perform(rosterRequest(singleStudentBody(2, 1L, 2, "바다", 2)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.student_count").value(2))
            .andExpect(jsonPath("$.data.state_version").value(2));

        assertEquals(
            2,
            studentRepository.countByClassroomIdAndStatus(
                classroomId,
                ClassroomStudentStatus.ACTIVE
            )
        );
        assertEquals(3, currentPolicyVersion());
    }

    @Test
    void rejectsDuplicateAttendanceNumberWithoutPartialWrites() throws Exception {
        String body = bulkBody(1, null)
            .replace("\"attendance_number\": 2", "\"attendance_number\": 1");

        mockMvc.perform(rosterRequest(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("DUPLICATE_ATTENDANCE_NUMBER"));

        assertEquals(0, studentRepository.count());
        assertEquals(0, salaryRoleRepository.count());
        assertEquals(1, currentPolicyVersion());
    }

    @Test
    void rejectsCaseInsensitiveNicknameConflictAgainstExistingRosterAtomically()
        throws Exception {
        mockMvc.perform(rosterRequest(singleStudentBody(1, null, 1, "Economy", 1)))
            .andExpect(status().isOk());

        mockMvc.perform(rosterRequest(singleStudentBody(2, 1L, 2, "economy", 2)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("DUPLICATE_NICKNAME"));

        assertEquals(1, studentRepository.count());
        assertEquals(2, currentPolicyVersion());
    }

    @Test
    void rejectsUpdatesForInactiveClassroom() throws Exception {
        Classroom classroom = classroomRepository.findById(classroomId).orElseThrow();
        classroom.changeStatus(ClassroomStatus.CLOSING, NOW.plusSeconds(60));

        mockMvc.perform(rosterRequest(singleStudentBody(1, 1L, 1, "하늘", 1)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("CLASS_NOT_ACTIVE"));

        assertTrue(studentRepository.findAll().isEmpty());
    }

    @Test
    void rejectsNonOwnerTeacher() throws Exception {
        UUID otherTeacherId = UUID.randomUUID();
        userAccountRepository.save(
            UserAccount.createTeacher(otherTeacherId, "other-teacher", "다른 교사", NOW)
        );

        mockMvc.perform(
            put("/classrooms/{classroomId}/roster-and-salaries", classroomId)
                .principal(() -> "other-teacher")
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(singleStudentBody(1, null, 1, "하늘", 1))
        )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("CLASS_NOT_ASSIGNED"));
    }

    @Test
    void registersRolelessStudentsWhenSalaryModuleIsDisabled() throws Exception {
        ClassroomModule salaryModule = moduleRepository.findAll().getFirst();
        salaryModule.disable(NOW.plusSeconds(30));
        moduleRepository.saveAndFlush(salaryModule);

        mockMvc.perform(rosterRequest("""
            {
              "students": [
                {"attendance_number": 1, "nickname": "NoJob"}
              ],
              "salary_roles": []
            }
            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.student_count").value(1))
            .andExpect(jsonPath("$.data.salary_role_count").value(0));

        assertEquals(1, studentRepository.count());
        assertEquals(null, studentRepository.findAll().getFirst().getSalaryRoleId());
        assertEquals(0, salaryRoleRepository.count());
        assertEquals(1, currentPolicyVersion());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
        rosterRequest(String body) {
        return put("/classrooms/{classroomId}/roster-and-salaries", classroomId)
            .principal(ownerPrincipal)
            .header("Idempotency-Key", UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    }

    private String bulkBody(long expectedPolicyVersion, Long expectedStateVersion) {
        String state = expectedStateVersion == null
            ? ""
            : "\"expected_state_version\": %d,".formatted(expectedStateVersion);
        return """
            {
              %s
              "students": [
                {"attendance_number": 1, "nickname": "하늘", "role_code": "LEADER"},
                {"attendance_number": 2, "nickname": "바다", "role_code": "MEMBER"}
              ],
              "salary_roles": [
                {
                  "role_code": "LEADER",
                  "name": "반장",
                  "gross_amount": 1200,
                  "deductions": {"tax_rate_bps": 1000},
                  "sort_order": 1
                },
                {
                  "role_code": "MEMBER",
                  "name": "구성원",
                  "gross_amount": 1000,
                  "deductions": {},
                  "sort_order": 2
                }
              ],
              "expected_policy_version": %d
            }
            """.formatted(state, expectedPolicyVersion);
    }

    private String singleStudentBody(
        long expectedPolicyVersion,
        Long expectedStateVersion,
        int attendanceNumber,
        String nickname,
        int sortOrder
    ) {
        String state = expectedStateVersion == null
            ? ""
            : "\"expected_state_version\": %d,".formatted(expectedStateVersion);
        return """
            {
              %s
              "students": [
                {
                  "attendance_number": %d,
                  "nickname": "%s",
                  "role_code": "MEMBER"
                }
              ],
              "salary_roles": [
                {
                  "role_code": "MEMBER",
                  "name": "구성원",
                  "gross_amount": 1000,
                  "deductions": {},
                  "sort_order": %d
                }
              ],
              "expected_policy_version": %d
            }
            """.formatted(
                state,
                attendanceNumber,
                nickname,
                sortOrder,
                expectedPolicyVersion
            );
    }

    private int currentPolicyVersion() {
        return policyVersionRepository
            .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(classroomId, "SALARY")
            .orElseThrow()
            .getVersion();
    }

    private static String randomClassroomCode() {
        String value = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return value.substring(0, 8).replace('I', 'J').replace('O', 'P').replace('L', 'M');
    }
}
