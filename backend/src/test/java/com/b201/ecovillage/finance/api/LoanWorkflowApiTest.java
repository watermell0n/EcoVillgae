package com.b201.ecovillage.finance.api;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.finance.application.LoanRepaymentProcessor;
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
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:loan-workflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.task.scheduling.enabled=false",
    "ecovillage.finance.loan-repayment-delay-ms=3600000",
    "ecovillage.ai-monitor.enabled=false"
})
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LoanWorkflowApiTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-03T01:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ClassroomRepository classroomRepository;
    @Autowired private ClassroomStudentRepository studentRepository;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private ClassroomTeacherRepository teacherRepository;
    @Autowired private ClassroomModuleRepository moduleRepository;
    @Autowired private LedgerAccountRepository ledgerAccountRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private LoanRepaymentProcessor repaymentProcessor;

    private UUID classroomId;
    private UUID studentId;
    private Principal owner;
    private Principal student;

    @BeforeEach
    void setUp() {
        createJdbcLoanTables();
        classroomId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        owner = () -> "loan-owner-" + classroomId;
        student = () -> studentId.toString();

        Classroom classroom = Classroom.active(classroomId, randomClassroomCode(), CREATED_AT);
        classroom.updateCourseConfiguration(
            4,
            CourseSize.SEMESTER,
            LocalDate.of(2026, 8, 3),
            LocalDate.of(2027, 2, 2),
            null,
            CREATED_AT
        );
        classroomRepository.save(classroom);
        studentRepository.save(ClassroomStudent.enroll(
            studentId,
            classroomId,
            1,
            "대출학생",
            CREATED_AT
        ));
        userRepository.save(UserAccount.createTeacher(
            teacherId,
            owner.getName(),
            "Loan Owner",
            CREATED_AT
        ));
        teacherRepository.save(ClassroomTeacher.assign(
            UUID.randomUUID(),
            classroomId,
            teacherId,
            ClassroomTeacherRole.OWNER,
            CREATED_AT
        ));
        ClassroomModule loanModule = ClassroomModule.disabled(
            UUID.randomUUID(), classroomId, "LOAN", CREATED_AT
        );
        loanModule.activate(Map.of(), CREATED_AT.plusSeconds(1));
        moduleRepository.save(loanModule);
        ClassroomModule creditModule = ClassroomModule.disabled(
            UUID.randomUUID(), classroomId, "CREDIT", CREATED_AT
        );
        creditModule.activate(Map.of(), CREATED_AT.plusSeconds(1));
        moduleRepository.save(creditModule);

        LedgerAccount wallet = LedgerAccount.studentWallet(
            UUID.randomUUID(), classroomId, studentId, CREATED_AT
        );
        wallet.post(1_000, CREATED_AT);
        ledgerAccountRepository.save(wallet);
        jdbcTemplate.update("""
            insert into credit_profiles(
                classroom_student_id,classroom_id,level,score,evaluated_at,created_at,updated_at
            ) values(?,?,'GOOD',800,?,?,?)
            """, studentId, classroomId, CREATED_AT, CREATED_AT, CREATED_AT);
    }

    @Test
    void appliesApprovesExposesScheduleAndPrepaysIdempotently() throws Exception {
        UUID applyKey = UUID.randomUUID();
        JsonNode application = apply(200, 4, applyKey);
        UUID applicationId = UUID.fromString(application.path("application_id").asText());
        assertEquals(applicationId.toString(), apply(200, 4, applyKey)
            .path("application_id").asText());
        assertEquals(1, jdbcTemplate.queryForObject(
            "select count(*) from loan_applications where classroom_id=?",
            Integer.class,
            classroomId
        ));

        mockMvc.perform(get("/classrooms/{classroomId}/loan-approval-queue", classroomId)
                .principal(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.applications[0].application_id")
                .value(applicationId.toString()))
            .andExpect(jsonPath("$.data.applications[0].credit_score").value(800));

        ObjectNode decision = objectMapper.createObjectNode();
        decision.put("decision", "APPROVE");
        decision.put("approved_amount", 200);
        decision.put("reason", "상환 계획 확인");
        UUID approvalKey = UUID.randomUUID();
        mockMvc.perform(post(
                "/classrooms/{classroomId}/loan-applications/{applicationId}/teacher-decisions",
                classroomId,
                applicationId
            ).principal(owner)
                .header("Idempotency-Key", approvalKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(decision.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"));
        mockMvc.perform(post(
                "/classrooms/{classroomId}/loan-applications/{applicationId}/teacher-decisions",
                classroomId,
                applicationId
            ).principal(owner)
                .header("Idempotency-Key", approvalKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(decision.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"));
        assertEquals(1, jdbcTemplate.queryForObject(
            "select count(*) from loans where classroom_id=?", Integer.class, classroomId
        ));
        assertEquals(1_200, jdbcTemplate.queryForObject(
            "select current_balance from ledger_accounts where classroom_id=? and classroom_student_id=? and account_type='STUDENT_WALLET'",
            Long.class,
            classroomId,
            studentId
        ));

        JsonNode overview = overview();
        JsonNode loan = overview.path("loans").get(0);
        UUID loanId = UUID.fromString(loan.path("loan_id").asText());
        assertEquals(600, loan.path("applied_interest_rate_bps").asInt());
        assertEquals(4, loan.path("installments").size());
        assertEquals("SCHEDULED", loan.path("installments").get(0).path("status").asText());

        mockMvc.perform(get("/classrooms/{classroomId}/loan-approval-queue", classroomId)
                .principal(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.applications").isEmpty())
            .andExpect(jsonPath("$.data.active_loans[0].loan_id").value(loanId.toString()))
            .andExpect(jsonPath("$.data.active_loans[0].installments.length()").value(4));

        UUID prepayKey = UUID.randomUUID();
        ObjectNode prepay = objectMapper.createObjectNode();
        prepay.put("expected_state_version", loan.path("state_version").asLong());
        prepay.put("command", "PREPAY_ALL");
        MvcResult first = mockMvc.perform(post(
                "/classrooms/{classroomId}/loans/{loanId}/repayment-commands",
                classroomId,
                loanId
            ).principal(student)
                .header("Idempotency-Key", prepayKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(prepay.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REPAID"))
            .andExpect(jsonPath("$.data.remaining_principal").value(0))
            .andExpect(jsonPath("$.data.remaining_interest").value(0))
            .andExpect(jsonPath("$.data.credit_score").value(820))
            .andExpect(jsonPath("$.data.replayed").value(false))
            .andReturn();

        mockMvc.perform(post(
                "/classrooms/{classroomId}/loans/{loanId}/repayment-commands",
                classroomId,
                loanId
            ).principal(student)
                .header("Idempotency-Key", prepayKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(prepay.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.financial_resource_id").value(loanId.toString()))
            .andExpect(jsonPath("$.data.replayed").value(true));

        JsonNode repaid = overview();
        assertEquals("REPAID", repaid.path("loans").get(0).path("status").asText());
        assertEquals("LOAN_REPAID", repaid.path("credit_events").get(0).path("event_type").asText());
        assertEquals(4, jdbcTemplate.queryForObject(
            "select count(*) from loan_installments where loan_id=? and status='PAID'",
            Integer.class,
            loanId
        ));
        assertEquals(
            objectMapper.readTree(first.getResponse().getContentAsByteArray())
                .path("data").path("remaining_principal").asLong(),
            repaid.path("loans").get(0).path("outstanding_principal").asLong()
        );
    }

    @Test
    void cancelsPendingApplicationBeforeTeacherDecision() throws Exception {
        UUID applicationId = UUID.fromString(apply(100, 4).path("application_id").asText());
        ObjectNode cancel = objectMapper.createObjectNode();
        cancel.put("command", "CANCEL");
        cancel.put("application_id", applicationId.toString());

        mockMvc.perform(post("/classrooms/{classroomId}/loan-application-commands", classroomId)
                .principal(student)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cancel.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELED"));

        mockMvc.perform(get("/classrooms/{classroomId}/loan-approval-queue", classroomId)
                .principal(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.applications").isEmpty());
    }

    @Test
    void marksMissedPaymentAndLetsStudentRetryWithinGraceWindow() throws Exception {
        UUID applicationId = UUID.fromString(apply(200, 4).path("application_id").asText());
        ObjectNode decision = objectMapper.createObjectNode();
        decision.put("decision", "APPROVE");
        decision.put("approved_amount", 200);
        mockMvc.perform(post(
                "/classrooms/{classroomId}/loan-applications/{applicationId}/teacher-decisions",
                classroomId,
                applicationId
            ).principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(decision.toString()))
            .andExpect(status().isOk());

        UUID loanId = jdbcTemplate.queryForObject(
            "select id from loans where loan_application_id=?", UUID.class, applicationId
        );
        Instant now = Instant.now();
        jdbcTemplate.update(
            "update loan_installments set due_at=?,grace_until=? where loan_id=? and installment_number=1",
            now.minusSeconds(60), now.plusSeconds(3600), loanId
        );
        jdbcTemplate.update(
            "update ledger_accounts set current_balance=0 where classroom_id=? and classroom_student_id=? and account_type='STUDENT_WALLET'",
            classroomId, studentId
        );

        repaymentProcessor.processClassroom(classroomId);
        assertEquals("OVERDUE", jdbcTemplate.queryForObject(
            "select status from loans where id=?", String.class, loanId
        ));
        assertEquals("MISSED", jdbcTemplate.queryForObject(
            "select status from loan_installments where loan_id=? and installment_number=1",
            String.class, loanId
        ));
        assertEquals(770, jdbcTemplate.queryForObject(
            "select score from credit_profiles where classroom_student_id=?", Integer.class, studentId
        ));

        jdbcTemplate.update(
            "update ledger_accounts set current_balance=1000 where classroom_id=? and classroom_student_id=? and account_type='STUDENT_WALLET'",
            classroomId, studentId
        );
        long stateVersion = jdbcTemplate.queryForObject(
            "select state_version from loans where id=?", Long.class, loanId
        );
        ObjectNode retry = objectMapper.createObjectNode();
        retry.put("expected_state_version", stateVersion);
        retry.put("command", "RETRY_DUE_PAYMENT");
        mockMvc.perform(post(
                "/classrooms/{classroomId}/loans/{loanId}/repayment-commands",
                classroomId,
                loanId
            ).principal(student)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(retry.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.credit_score").value(770));

        assertEquals("PAID", jdbcTemplate.queryForObject(
            "select status from loan_installments where loan_id=? and installment_number=1",
            String.class, loanId
        ));
    }

    @Test
    void protectsCreditDependencyAndRequiresLoanSettlementBeforeStop() throws Exception {
        apply(100, 4);
        ObjectNode stop = objectMapper.createObjectNode();
        stop.put("reason", "대출 운영 종료");

        mockMvc.perform(post(
                "/classrooms/{classroomId}/modules/CREDIT/stop-commands", classroomId
            ).principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(stop.toString()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("ACTIVE_DEPENDENT_MODULE_EXISTS"));

        mockMvc.perform(post(
                "/classrooms/{classroomId}/modules/LOAN/stop-commands", classroomId
            ).principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(stop.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("STOPPING"))
            .andExpect(jsonPath("$.data.cleanup_blockers[0]")
                .value("LOAN_SETTLEMENT_REQUIRED"));

        mockMvc.perform(post(
                "/classrooms/{classroomId}/modules/CREDIT/stop-commands", classroomId
            ).principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(stop.toString()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("ACTIVE_DEPENDENT_MODULE_EXISTS"));
    }

    @Test
    void updatesLoanPolicyIdempotently() throws Exception {
        ObjectNode policy = objectMapper.createObjectNode();
        policy.put("maximum_principal", 400);
        policy.put("interest_rate_bps", 800);
        policy.put("term_units", 28);
        policy.put("installment_count", 4);
        policy.put("grace_units", 3);
        policy.put("on_time_credit", 10);
        policy.put("overdue_credit", -30);
        policy.put("repaid_credit", 20);
        policy.put("expected_policy_version", 1);
        UUID requestId = UUID.randomUUID();

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(put("/classrooms/{classroomId}/loan-policy", classroomId)
                    .principal(owner)
                    .header("Idempotency-Key", requestId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(policy.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.maximum_principal").value(400));
        }

        assertEquals(2, jdbcTemplate.queryForObject(
            "select count(*) from loan_policy_versions where classroom_id=?",
            Integer.class,
            classroomId
        ));
        policy.put("maximum_principal", 500);
        mockMvc.perform(put("/classrooms/{classroomId}/loan-policy", classroomId)
                .principal(owner)
                .header("Idempotency-Key", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(policy.toString()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    private JsonNode apply(long amount, int installments) throws Exception {
        return apply(amount, installments, UUID.randomUUID());
    }

    private JsonNode apply(long amount, int installments, UUID requestId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("command", "APPLY");
        body.put("amount", amount);
        body.put("installments", installments);
        MvcResult result = mockMvc.perform(
                post("/classrooms/{classroomId}/loan-application-commands", classroomId)
                    .principal(student)
                    .header("Idempotency-Key", requestId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body.toString())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPLIED"))
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }

    private JsonNode overview() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/classrooms/{classroomId}/students/me/loans-and-credit", classroomId)
                    .principal(student)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.credit_score").isNumber())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }

    private void createJdbcLoanTables() {
        jdbcTemplate.execute("""
            create table if not exists loan_applications(
                id uuid primary key,classroom_id uuid not null,classroom_student_id uuid not null,
                loan_policy_version_id uuid not null,requested_amount bigint not null,
                approved_amount bigint,credit_level_snapshot varchar(40) not null,
                credit_score_snapshot integer not null,decision_reason varchar(1000),
                decided_by_user_id uuid,decided_at timestamp with time zone,status varchar(40) not null,
                state_version bigint default 0 not null,status_changed_at timestamp with time zone not null,
                created_at timestamp with time zone not null,updated_at timestamp with time zone not null
            )
            """);
        jdbcTemplate.execute("""
            create table if not exists loans(
                id uuid primary key,classroom_id uuid not null,loan_application_id uuid not null unique,
                principal_amount bigint not null,applied_interest_rate_bps integer not null,
                credit_level_at_approval varchar(40) not null,outstanding_principal bigint not null,
                outstanding_interest bigint default 0 not null,disbursement_transaction_group_id uuid not null,
                started_at timestamp with time zone not null,matures_at timestamp with time zone not null,
                closed_at timestamp with time zone,status varchar(40) not null,state_version bigint default 0 not null,
                status_changed_at timestamp with time zone not null,created_at timestamp with time zone not null,
                updated_at timestamp with time zone not null
            )
            """);
        jdbcTemplate.execute("""
            create table if not exists loan_installments(
                id uuid primary key,classroom_id uuid not null,loan_id uuid not null,
                installment_number integer not null,scheduled_principal bigint not null,
                scheduled_interest bigint not null,due_at timestamp with time zone not null,
                grace_until timestamp with time zone not null,paid_principal bigint default 0 not null,
                paid_interest bigint default 0 not null,paid_at timestamp with time zone,
                transaction_group_id uuid,status varchar(40) not null,state_version bigint default 0 not null,
                status_changed_at timestamp with time zone not null,created_at timestamp with time zone not null,
                updated_at timestamp with time zone not null,unique(loan_id,installment_number)
            )
            """);
        jdbcTemplate.execute("""
            create table if not exists credit_profiles(
                classroom_student_id uuid primary key,classroom_id uuid not null,level varchar(40) not null,
                score integer not null,state_version bigint default 0 not null,
                evaluated_at timestamp with time zone not null,created_at timestamp with time zone not null,
                updated_at timestamp with time zone not null,unique(classroom_id,classroom_student_id)
            )
            """);
        jdbcTemplate.execute("""
            create table if not exists credit_events(
                id uuid primary key,classroom_id uuid not null,classroom_student_id uuid not null,
                event_type varchar(40) not null,score_delta integer not null,score_before integer not null,
                score_after integer not null,level_before varchar(40) not null,level_after varchar(40) not null,
                reason varchar(1000) not null,related_type varchar(40),related_id uuid,
                occurred_at timestamp with time zone not null,created_at timestamp with time zone not null
            )
            """);
    }

    private static String randomClassroomCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
