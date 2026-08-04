package com.b201.ecovillage.classroom.api;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.domain.SalaryRole;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.classroom.repository.ModuleStateTransitionRepository;
import com.b201.ecovillage.classroom.repository.SalaryRoleRepository;
import com.b201.ecovillage.course.domain.CourseTemplate;
import com.b201.ecovillage.course.domain.CourseTemplateVersion;
import com.b201.ecovillage.course.repository.CourseTemplateRepository;
import com.b201.ecovillage.course.repository.CourseTemplateVersionRepository;
import com.b201.ecovillage.finance.domain.DepositAccount;
import com.b201.ecovillage.finance.domain.FinancialProduct;
import com.b201.ecovillage.finance.domain.FinancialProductType;
import com.b201.ecovillage.finance.domain.FinancialProductVersion;
import com.b201.ecovillage.finance.repository.DepositAccountRepository;
import com.b201.ecovillage.finance.repository.FinancialProductRepository;
import com.b201.ecovillage.finance.repository.FinancialProductVersionRepository;
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
import com.b201.ecovillage.quiz.domain.ActivitySession;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:classroom-mutations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class ClassroomMutationApiTest {

    private static final Instant NOW = Instant.parse("2026-08-03T03:00:00Z");
    private static final String OWNER_SUBJECT = "mutation-owner";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomTeacherRepository teacherRepository;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private ClassroomStudentRepository studentRepository;

    @Autowired
    private ClassroomModuleRepository moduleRepository;

    @Autowired
    private ClassroomPolicyVersionRepository policyRepository;

    @Autowired
    private ModuleStateTransitionRepository transitionRepository;

    @Autowired
    private SalaryRoleRepository salaryRoleRepository;

    @Autowired
    private ActivitySessionRepository activityRepository;

    @Autowired
    private CourseTemplateRepository courseTemplateRepository;

    @Autowired
    private CourseTemplateVersionRepository courseVersionRepository;

    @Autowired
    private FinancialProductRepository productRepository;

    @Autowired
    private FinancialProductVersionRepository productVersionRepository;

    @Autowired
    private DepositAccountRepository depositAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID classroomId;
    private UUID teacherId;
    private UUID courseVersionId;
    private ClassroomStudent student;
    private Principal owner;

    @BeforeEach
    void setUp() {
        classroomId = UUID.randomUUID();
        teacherId = UUID.randomUUID();
        owner = () -> OWNER_SUBJECT;
        userRepository.save(
            UserAccount.createTeacher(teacherId, OWNER_SUBJECT, "Mutation Owner", NOW)
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
        teacherRepository.save(ClassroomTeacher.assign(
            UUID.randomUUID(),
            classroomId,
            teacherId,
            ClassroomTeacherRole.OWNER,
            NOW
        ));
        student = studentRepository.save(ClassroomStudent.enroll(
            UUID.randomUUID(),
            classroomId,
            1,
            "Student",
            NOW
        ));

        UUID courseTemplateId = UUID.randomUUID();
        courseVersionId = UUID.randomUUID();
        courseTemplateRepository.save(CourseTemplate.published(
            courseTemplateId,
            "G6-SEMESTER",
            "Grade 6 Semester",
            6,
            CourseSize.SEMESTER,
            NOW
        ));
        courseVersionRepository.save(CourseTemplateVersion.published(
            courseVersionId,
            courseTemplateId,
            1,
            Map.of("difficulty_profile", "GRADE_6_ADVANCED"),
            NOW
        ));
    }

    @Test
    void updatesCourseConfigurationWithVersionHistoryAndReadBack() throws Exception {
        UUID requestId = UUID.randomUUID();
        String body = courseBody(0, 0, null, null);

        mockMvc.perform(courseRequest(requestId, body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.policy_version").value(1))
            .andExpect(jsonPath("$.data.state_version").value(1))
            .andExpect(jsonPath("$.data.replayed").value(false));

        mockMvc.perform(courseRequest(requestId, body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.policy_version").value(1))
            .andExpect(jsonPath("$.data.replayed").value(true));

        mockMvc.perform(
            get("/classrooms/{classroomId}/configuration", classroomId)
                .principal(owner)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.display_name").value("6학년 3반"))
            .andExpect(jsonPath("$.data.class_number").value(3))
            .andExpect(jsonPath("$.data.description").value("투자 심화반"))
            .andExpect(jsonPath("$.data.grade").value(6))
            .andExpect(jsonPath("$.data.course_size").value("SEMESTER"))
            .andExpect(jsonPath("$.data.starts_on").value("2026-09-01"))
            .andExpect(jsonPath("$.data.ends_on").value("2026-12-20"))
            .andExpect(jsonPath("$.data.recommended_course_id")
                .value(courseVersionId.toString()))
            .andExpect(jsonPath("$.data.difficulty_profile")
                .value("GRADE_6_ADVANCED"));

        mockMvc.perform(courseRequest(
            UUID.randomUUID(),
            courseBody(1, 1, null, null)
        ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.policy_version").value(2));

        assertEquals(
            2,
            policyRepository.findAllByClassroomIdAndPolicyAreaOrderByVersion(
                classroomId,
                "COURSE_CONFIGURATION"
            ).size()
        );
        assertEquals(
            "SUPERSEDED",
            policyRepository.findAllByClassroomIdAndPolicyAreaOrderByVersion(
                classroomId,
                "COURSE_CONFIGURATION"
            ).getFirst().getStatus()
        );
    }

    @Test
    void rejectsImmutableCourseFieldsAndActiveActivityWithoutPartialWrite()
        throws Exception {
        String originalCurrency = classroomRepository.findById(classroomId)
            .orElseThrow()
            .getCurrencyName();
        mockMvc.perform(courseRequest(
            UUID.randomUUID(),
            courseBody(0, 0, "CHANGED1", "OTHER")
        ))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("IMMUTABLE_CLASSROOM_FIELD"));

        activityRepository.save(ActivitySession.liveQuiz(
            UUID.randomUUID(),
            classroomId,
            teacherId,
            false,
            NOW
        ));
        mockMvc.perform(courseRequest(
            UUID.randomUUID(),
            courseBody(0, 0, null, null)
        ))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("ACTIVITY_IN_PROGRESS"));

        assertEquals(0, policyRepository.count());
        Classroom unchanged = classroomRepository.findById(classroomId).orElseThrow();
        assertEquals(5, unchanged.getGrade());
        assertEquals(originalCurrency, unchanged.getCurrencyName());
    }

    @Test
    void movesSalaryModuleToStoppingAndRetainsRolesForSettlement() throws Exception {
        SalaryRole role = salaryRoleRepository.save(SalaryRole.create(
            UUID.randomUUID(),
            classroomId,
            "LEADER",
            "Leader",
            1001,
            Map.of(),
            1,
            NOW
        ));
        student.assignSalaryRole(role.getId(), NOW);
        studentRepository.save(student);
        ClassroomModule module = ClassroomModule.disabled(
            UUID.randomUUID(),
            classroomId,
            "ROLE_SALARY",
            NOW
        );
        module.activate(Map.of("policy_area", "SALARY", "policy_version", 1), NOW);
        moduleRepository.save(module);
        activityRepository.save(ActivitySession.liveQuiz(
            UUID.randomUUID(),
            classroomId,
            teacherId,
            false,
            NOW
        ));
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(stopRequest(requestId, "ROLE_SALARY", 0))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("STOPPING"))
            .andExpect(jsonPath("$.data.cleanup_required").value(true))
            .andExpect(jsonPath("$.data.cleanup_blockers")
                .value(hasItems(
                    "LIVE_ACTIVITY_EXISTS",
                    "SALARY_ROLE_SETTLEMENT_REQUIRED"
                )))
            .andExpect(jsonPath("$.data.replayed").value(false));

        mockMvc.perform(stopRequest(requestId, "ROLE_SALARY", 0))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.replayed").value(true));

        assertEquals("ACTIVE", salaryRoleRepository.findById(role.getId())
            .orElseThrow().getStatus());
        assertEquals(role.getId(), studentRepository.findById(student.getId())
            .orElseThrow().getSalaryRoleId());
        assertEquals(1, transitionRepository.count());

        mockMvc.perform(
            get("/classrooms/{classroomId}/modules-and-policies", classroomId)
                .principal(owner)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.modules[0].status").value("STOPPING"))
            .andExpect(jsonPath("$.data.modules[0].config.cleanup_required").value(true));
    }

    @Test
    void rejectsInactiveModuleAndStaleModuleVersion() throws Exception {
        moduleRepository.save(ClassroomModule.disabled(
            UUID.randomUUID(),
            classroomId,
            "STORE",
            NOW
        ));
        mockMvc.perform(stopRequest(UUID.randomUUID(), "STORE", 0))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("MODULE_NOT_ACTIVE"));

        ClassroomModule active = ClassroomModule.disabled(
            UUID.randomUUID(),
            classroomId,
            "QUIZ",
            NOW
        );
        active.activate(Map.of(), NOW);
        moduleRepository.save(active);
        mockMvc.perform(stopRequest(UUID.randomUUID(), "QUIZ", 9))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("STATE_VERSION_CONFLICT"));

        assertEquals(0, transitionRepository.count());
    }

    @Test
    void versionsEconomicPolicyWithoutChangingExistingDepositTerms() throws Exception {
        FinancialProductVersion productVersion = activeDeposit();
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(economicPolicyRequest(requestId, 0, 0, 300, 700, "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.policy_version").value(1))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.state_version").value(1))
            .andExpect(jsonPath("$.data.replayed").value(false));

        mockMvc.perform(economicPolicyRequest(requestId, 0, 0, 300, 700, "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.replayed").value(true));

        assertEquals(
            250,
            productVersionRepository.findById(productVersion.getId())
                .orElseThrow().getInterestRateBps()
        );
        assertEquals(
            25,
            depositAccountRepository.findAll().getFirst().getExpectedInterestAmount()
        );

        mockMvc.perform(economicPolicyRequest(
            UUID.randomUUID(),
            1,
            1,
            350,
            750,
            "1"
        ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.policy_version").value(2));

        mockMvc.perform(
            get("/classrooms/{classroomId}/modules-and-policies", classroomId)
                .principal(owner)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.policy_versions[0].policy_area")
                .value("ECONOMIC"))
            .andExpect(jsonPath("$.data.policy_versions[0].values.deposit_rate_bps")
                .value(350))
            .andExpect(jsonPath("$.data.policy_versions[0].values.amount_unit")
                .value(1));

        assertEquals(
            2,
            policyRepository.findAllByClassroomIdAndPolicyAreaOrderByVersion(
                classroomId,
                "ECONOMIC"
            ).size()
        );
        assertEquals(
            2,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM policy_snapshots WHERE classroom_id = ?",
                Integer.class,
                classroomId
            )
        );
    }

    @Test
    void rejectsStaleOrInvalidEconomicPolicyWithoutAdvancingVersion() throws Exception {
        mockMvc.perform(economicPolicyRequest(
            UUID.randomUUID(),
            0,
            1,
            300,
            700,
            "7"
        ))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("POLICY_VERSION_CONFLICT"));

        mockMvc.perform(economicPolicyRequest(
            UUID.randomUUID(),
            0,
            0,
            300,
            400,
            "7"
        ))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("POLICY_VIOLATION"));

        mockMvc.perform(economicPolicyRequest(
            UUID.randomUUID(),
            0,
            0,
            300,
            700,
            "7.5"
        ))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("POLICY_VIOLATION"));

        assertEquals(0, policyRepository.count());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void rollsBackPolicySnapshotAndClassroomWhenStorageFails() throws Exception {
        jdbcTemplate.execute("""
            ALTER TABLE policy_snapshots
            ADD CONSTRAINT ck_test_snapshot_rounding
            CHECK (rounding_rule = 'BLOCKED')
            """);

        mockMvc.perform(economicPolicyRequest(
            UUID.randomUUID(),
            0,
            0,
            300,
            700,
            "7"
        ))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("POLICY_VERSION_CONFLICT"));

        assertEquals(0, policyRepository.count());
        assertEquals(
            0,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM policy_snapshots WHERE classroom_id = ?",
                Integer.class,
                classroomId
            )
        );
        assertEquals(
            0,
            classroomRepository.findById(classroomId).orElseThrow().getStateVersion()
        );
    }

    private FinancialProductVersion activeDeposit() {
        UUID productId = UUID.randomUUID();
        FinancialProduct product = FinancialProduct.draft(
            productId,
            classroomId,
            FinancialProductType.DEPOSIT,
            "Existing Deposit",
            "Keeps the subscribed rate",
            NOW
        );
        product.publish(NOW);
        productRepository.save(product);
        FinancialProductVersion version = productVersionRepository.save(
            FinancialProductVersion.issue(
                UUID.randomUUID(),
                classroomId,
                productId,
                1,
                FinancialProductType.DEPOSIT,
                30,
                250,
                100,
                null,
                null,
                1,
                10_000,
                true,
                NOW
            )
        );
        depositAccountRepository.save(DepositAccount.open(
            UUID.randomUUID(),
            classroomId,
            student.getId(),
            version.getId(),
            1000,
            25,
            NOW,
            NOW.plusSeconds(30L * 86_400L),
            UUID.randomUUID()
        ));
        return version;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
        courseRequest(UUID requestId, String body) {
        return put("/classrooms/{classroomId}/course-configuration", classroomId)
            .principal(owner)
            .header("Idempotency-Key", requestId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    }

    private String courseBody(
        long expectedStateVersion,
        int expectedPolicyVersion,
        String classroomCode,
        String currencyName
    ) {
        String immutable = classroomCode == null
            ? ""
            : ", \"classroom_code\": \"" + classroomCode
                + "\", \"currency_name\": \"" + currencyName + "\"";
        return """
            {
              "expected_state_version": %d,
              "display_name": "6학년 3반",
              "class_number": 3,
              "description": "투자 심화반",
              "grade": 6,
              "course_size": "SEMESTER",
              "starts_on": "2026-09-01",
              "ends_on": "2026-12-20",
              "recommended_course_id": "%s",
              "difficulty_profile": "GRADE_6_ADVANCED",
              "expected_policy_version": %d%s
            }
            """.formatted(
                expectedStateVersion,
                courseVersionId,
                expectedPolicyVersion,
                immutable
            );
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
        stopRequest(UUID requestId, String moduleCode, long expectedStateVersion) {
        return post(
            "/classrooms/{classroomId}/modules/{moduleCode}/stop-commands",
            classroomId,
            moduleCode
        )
            .principal(owner)
            .header("Idempotency-Key", requestId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "expected_state_version": %d,
                  "reason": "Finish this module safely"
                }
                """.formatted(expectedStateVersion));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
        economicPolicyRequest(
            UUID requestId,
            long expectedStateVersion,
            int expectedPolicyVersion,
            int depositRate,
            int loanRate,
            String rewardAmount
        ) {
        return put("/classrooms/{classroomId}/economic-policy", classroomId)
            .principal(owner)
            .header("Idempotency-Key", requestId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "expected_state_version": %d,
                  "price_adjustment_bps": -50,
                  "inflation_rate_bps": 100,
                  "income_tax_rate_bps": 500,
                  "vat_rate_bps": 1000,
                  "trade_tax_rate_bps": 300,
                  "deposit_rate_bps": %d,
                  "loan_rate_bps": %d,
                  "stock_policy": {"default_stock": 3},
                  "reward_policy": {"default_amount": %s},
                  "limits": {"daily_amount": 1001},
                  "expected_policy_version": %d
                }
                """.formatted(
                    expectedStateVersion,
                    depositRate,
                    loanRate,
                    rewardAmount,
                    expectedPolicyVersion
                ));
    }

    private static String randomClassroomCode() {
        String value = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return value.substring(0, 8).replace('I', 'J').replace('O', 'P').replace('L', 'M');
    }
}
