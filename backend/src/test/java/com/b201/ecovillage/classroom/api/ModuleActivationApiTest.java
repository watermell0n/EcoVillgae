package com.b201.ecovillage.classroom.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.classroom.repository.SalaryRoleRepository;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomTeacher;
import com.b201.ecovillage.identity.domain.ClassroomTeacherRole;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomTeacherRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:module-activation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class ModuleActivationApiTest {

    private static final String OWNER_SUBJECT = "module-owner";
    private static final Instant NOW = Instant.parse("2026-07-31T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomTeacherRepository classroomTeacherRepository;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private ClassroomModuleRepository moduleRepository;

    @Autowired
    private ClassroomPolicyVersionRepository policyRepository;

    @Autowired
    private SalaryRoleRepository salaryRoleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID classroomId;
    private UUID teacherId;
    private Principal owner;

    @BeforeEach
    void setUp() {
        classroomId = UUID.randomUUID();
        teacherId = UUID.randomUUID();
        owner = () -> OWNER_SUBJECT;
        userRepository.save(
            UserAccount.createTeacher(teacherId, OWNER_SUBJECT, "Module Owner", NOW)
        );
        classroomRepository.save(Classroom.create(
            classroomId,
            UUID.randomUUID(),
            randomClassroomCode(),
            "ECO",
            5,
            CourseSize.ONE_MONTH,
            24,
            LocalDate.parse("2026-08-01"),
            LocalDate.parse("2026-08-31"),
            "Asia/Seoul",
            NOW
        ));
        classroomTeacherRepository.save(ClassroomTeacher.assign(
            UUID.randomUUID(),
            classroomId,
            teacherId,
            ClassroomTeacherRole.OWNER,
            NOW
        ));
        moduleRepository.save(ClassroomModule.disabled(
            UUID.randomUUID(),
            classroomId,
            "DEPOSIT",
            NOW
        ));
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
    void activatesModuleAndInitialPolicyAtomicallyAndReplaysDuplicateCommand()
        throws Exception {
        UUID requestId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO ai_runs VALUES (?, 'SETUP_PRECHECK', 'COMPLETED', 0, ?, ?)",
            classroomId,
            NOW,
            NOW
        );

        mockMvc.perform(activationRequest("DEPOSIT", requestId, 0, "{\"rate_bps\": 300}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.module_code").value("DEPOSIT"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.state_version").value(1))
            .andExpect(jsonPath("$.data.policy_area").value("DEPOSIT_RATE"))
            .andExpect(jsonPath("$.data.policy_version").value(1))
            .andExpect(jsonPath("$.data.rule_version").value("rule-engine-v2"))
            .andExpect(jsonPath("$.data.replayed").value(false));

        assertEquals(1, policyRepository.count());
        assertEquals(
            "EXPIRED",
            jdbcTemplate.queryForObject(
                "SELECT status FROM ai_runs WHERE classroom_id = ?",
                String.class,
                classroomId
            )
        );

        mockMvc.perform(activationRequest("DEPOSIT", requestId, 0, "{\"rate_bps\": 300}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.replayed").value(true))
            .andExpect(jsonPath("$.data.policy_version").value(1));

        assertEquals(1, policyRepository.count());
    }

    @Test
    void rejectsMissingOrInvalidInitialPolicyWithoutChangingModule() throws Exception {
        mockMvc.perform(activationRequest("DEPOSIT", UUID.randomUUID(), 0, null))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("INITIAL_POLICY_REQUIRED"));

        mockMvc.perform(activationRequest(
            "DEPOSIT",
            UUID.randomUUID(),
            0,
            "{\"rate_bps\": 700}"
        ))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("POLICY_VIOLATION"));

        ClassroomModule module = moduleRepository
            .findByClassroomIdAndModuleCode(classroomId, "DEPOSIT")
            .orElseThrow();
        assertEquals("DISABLED", module.getStatus());
        assertEquals(0, module.getStateVersion());
        assertEquals(0, policyRepository.count());
    }

    @Test
    void requiresActiveDepositBeforeLoanActivation() throws Exception {
        moduleRepository.save(ClassroomModule.disabled(
            UUID.randomUUID(),
            classroomId,
            "LOAN",
            NOW
        ));

        mockMvc.perform(activationRequest(
            "LOAN",
            UUID.randomUUID(),
            0,
            "{\"rate_bps\": 700}"
        ))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("PREREQUISITE_MODULE_REQUIRED"));

        assertEquals("DISABLED", moduleRepository
            .findByClassroomIdAndModuleCode(classroomId, "LOAN")
            .orElseThrow()
            .getStatus());
    }

    @Test
    void createsSalaryRolesOnlyWhenRoleSalaryModuleIsActivated() throws Exception {
        moduleRepository.save(ClassroomModule.disabled(
            UUID.randomUUID(),
            classroomId,
            "ROLE_SALARY",
            NOW
        ));
        String policy = """
            {
              "pay_interval_days": 7,
              "roles": [
                {
                  "role_code": "MEMBER",
                  "name": "Member",
                  "weight": 1.0,
                  "gross_amount": 1000,
                  "deductions": {},
                  "sort_order": 1
                }
              ]
            }
            """;

        mockMvc.perform(activationRequest(
            "ROLE_SALARY",
            UUID.randomUUID(),
            0,
            policy
        ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.policy_area").value("SALARY"));

        assertEquals(1, salaryRoleRepository.count());
        assertEquals(1, policyRepository.count());
    }

    @Test
    void reactivationReusesUnchangedCurrentPolicy() throws Exception {
        mockMvc.perform(activationRequest(
            "DEPOSIT",
            UUID.randomUUID(),
            0,
            "{\"rate_bps\": 300}"
        )).andExpect(status().isOk());

        ClassroomModule module = moduleRepository
            .findByClassroomIdAndModuleCode(classroomId, "DEPOSIT")
            .orElseThrow();
        module.disable(NOW.plusSeconds(60));
        moduleRepository.saveAndFlush(module);
        long disabledVersion = module.getStateVersion();

        mockMvc.perform(activationRequest(
            "DEPOSIT",
            UUID.randomUUID(),
            disabledVersion,
            null
        ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.policy_version").value(1));

        assertEquals(1, policyRepository.count());
    }

    @Test
    void rejectsStaleModuleVersion() throws Exception {
        mockMvc.perform(activationRequest(
            "DEPOSIT",
            UUID.randomUUID(),
            9,
            "{\"rate_bps\": 300}"
        ))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("STATE_VERSION_CONFLICT"));

        assertTrue(policyRepository.findAll().isEmpty());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
        activationRequest(
            String moduleCode,
            UUID requestId,
            long expectedStateVersion,
            String initialPolicyValues
        ) {
        String policy = initialPolicyValues == null
            ? ""
            : ", \"initial_policy_values\": " + initialPolicyValues;
        return post(
            "/classrooms/{classroomId}/modules/{moduleCode}/activation-commands",
            classroomId,
            moduleCode
        )
            .principal(owner)
            .header("Idempotency-Key", requestId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "expected_state_version": %d,
                  "confirm_prerequisites": true%s
                }
                """.formatted(expectedStateVersion, policy));
    }

    private static String randomClassroomCode() {
        String value = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return value.substring(0, 8).replace('I', 'J').replace('O', 'P').replace('L', 'M');
    }
}
