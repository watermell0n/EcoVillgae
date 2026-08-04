package com.b201.ecovillage.finance.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.sql.Timestamp;
import java.time.Instant;
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
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomTeacher;
import com.b201.ecovillage.identity.domain.ClassroomTeacherRole;
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
    "spring.datasource.url=jdbc:h2:mem:deposit-workflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.task.scheduling.enabled=false",
    "ecovillage.ai-monitor.enabled=false"
})
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DepositWorkflowApiTest {

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

    private UUID classroomId;
    private UUID studentId;
    private Principal owner;
    private Principal student;
    private LedgerAccount wallet;

    @BeforeEach
    void setUp() {
        classroomId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        owner = () -> "deposit-owner-" + classroomId;
        student = () -> studentId.toString();

        classroomRepository.save(Classroom.active(
            classroomId,
            randomClassroomCode(),
            CREATED_AT
        ));
        studentRepository.save(ClassroomStudent.enroll(
            studentId,
            classroomId,
            1,
            "student",
            CREATED_AT
        ));
        userRepository.save(UserAccount.createTeacher(
            teacherId,
            owner.getName(),
            "Deposit Owner",
            CREATED_AT
        ));
        teacherRepository.save(ClassroomTeacher.assign(
            UUID.randomUUID(),
            classroomId,
            teacherId,
            ClassroomTeacherRole.OWNER,
            CREATED_AT
        ));
        moduleRepository.save(ClassroomModule.disabled(
            UUID.randomUUID(),
            classroomId,
            "DEPOSIT",
            CREATED_AT
        ));
        wallet = LedgerAccount.studentWallet(
            UUID.randomUUID(),
            classroomId,
            studentId,
            CREATED_AT
        );
        wallet.post(5_000, CREATED_AT);
        ledgerAccountRepository.save(wallet);
    }

    @Test
    void rejectsSubscriptionWhileDepositModuleIsDisabled() throws Exception {
        UUID productId = createAndPublishProduct();

        mockMvc.perform(subscriptionRequest(UUID.randomUUID(), productId, 1_000))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("MODULE_NOT_ACTIVE"));
        assertEquals(5_000, walletBalance());
    }

    @Test
    void rejectsInsufficientBalanceAndStoppedProductWithoutCreatingAnAccount()
        throws Exception {
        activateDepositModule();
        UUID productId = createAndPublishProduct();

        mockMvc.perform(subscriptionRequest(UUID.randomUUID(), productId, 6_000))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_BALANCE"));
        assertEquals(5_000, walletBalance());
        assertEquals(0, depositAccountCount());

        productCommand(UUID.randomUUID(), "STOP", productId, 2);
        mockMvc.perform(subscriptionRequest(UUID.randomUUID(), productId, 100))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_ACTIVE"));
        assertEquals(5_000, walletBalance());
        assertEquals(0, depositAccountCount());
    }

    @Test
    void runsPublishedProductSubscriptionMaturityAndEarlyTerminationEndToEnd()
        throws Exception {
        activateDepositModule();
        UUID productId = createAndPublishProduct();

        mockMvc.perform(get("/classrooms/{classroomId}/financial-products", classroomId)
                .principal(student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.financial_products[0].resource_id")
                .value(productId.toString()))
            .andExpect(jsonPath("$.data.financial_products[0].version").value(2))
            // 학생 앱은 이 값으로 가입 가능 여부를 판단합니다. PUBLISHED 외의 값으로 바뀌면
            // 학생 은행 화면이 조용히 빈 목록이 되므로 계약으로 고정합니다.
            .andExpect(jsonPath("$.data.financial_products[0].status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.financial_products[0].product_type")
                .value("DEPOSIT"))
            .andExpect(jsonPath("$.data.financial_products[0].minimum_amount").value(100))
            .andExpect(jsonPath("$.data.financial_products[0].maximum_amount").value(10_000));

        UUID openKey = UUID.randomUUID();
        JsonNode opened = subscribe(openKey, productId, 1_000);
        UUID accountId = UUID.fromString(
            opened.path("data").path("financial_resource_id").asText()
        );
        JsonNode replayed = subscribe(openKey, productId, 1_000);
        assertEquals(opened.path("data"), replayed.path("data"));
        mockMvc.perform(subscriptionRequest(openKey, productId, 500))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertEquals(4_000, walletBalance());
        assertEquals(1_000, liabilityBalance());

        mockMvc.perform(get(
                "/classrooms/{classroomId}/students/me/deposit-and-savings",
                classroomId
            ).principal(student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deposit_accounts[0].resource_id")
                .value(accountId.toString()))
            .andExpect(jsonPath("$.data.deposit_accounts[0].expected_interest_amount")
                .value(50))
            .andExpect(jsonPath("$.data.deposit_accounts[0].maturity_amount")
                .value(1_050));

        jdbcTemplate.update(
            "UPDATE deposit_accounts SET matures_at = ? WHERE id = ?",
            Timestamp.from(Instant.now().minusSeconds(1)),
            accountId
        );
        mockMvc.perform(get(
                "/classrooms/{classroomId}/students/me/deposit-and-savings",
                classroomId
            ).principal(student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deposit_accounts[0].status").value("MATURED"));
        assertEquals(5_050, walletBalance());
        assertEquals(0, liabilityBalance());
        mockMvc.perform(get(
                "/classrooms/{classroomId}/students/me/deposit-and-savings",
                classroomId
            ).principal(student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deposit_accounts[0].status").value("MATURED"));
        assertEquals(5_050, walletBalance());

        UUID secondAccountId = UUID.fromString(
            subscribe(UUID.randomUUID(), productId, 500)
                .path("data")
                .path("financial_resource_id")
                .asText()
        );
        UUID terminateKey = UUID.randomUUID();
        JsonNode terminated = terminate(terminateKey, secondAccountId);
        JsonNode terminatedReplay = terminate(terminateKey, secondAccountId);
        assertEquals(terminated.path("data"), terminatedReplay.path("data"));
        assertEquals("EARLY_TERMINATED", terminated.path("data").path("status").asText());
        assertEquals(5_055, walletBalance());
        assertEquals(0, liabilityBalance());
    }

    private UUID createAndPublishProduct() throws Exception {
        JsonNode created = productCommand(
            UUID.randomUUID(),
            "CREATE",
            null,
            null
        );
        UUID productId = UUID.fromString(
            created.path("data").path("financial_resource_id").asText()
        );
        JsonNode published = productCommand(
            UUID.randomUUID(),
            "PUBLISH",
            productId,
            1
        );
        assertEquals("PUBLISHED", published.path("data").path("status").asText());
        return productId;
    }

    private JsonNode productCommand(
        UUID idempotencyKey,
        String command,
        UUID productId,
        Integer expectedProductVersion
    ) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("expected_state_version", 0);
        body.put("command", command);
        if (productId != null) {
            body.put("product_id", productId.toString());
        }
        body.put("product_type", "DEPOSIT");
        body.put("name", "One-day deposit");
        body.put("term_units", 1);
        body.put("interest_rate_bps", 500);
        body.put("early_termination_rate_bps", 100);
        body.put("minimum_amount", 100);
        body.put("maximum_amount", 10_000);
        if (expectedProductVersion != null) {
            body.put("expected_product_version", expectedProductVersion);
        }
        MvcResult result = mockMvc.perform(post(
                "/classrooms/{classroomId}/financial-product-commands",
                classroomId
            )
                .principal(owner)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode subscribe(UUID idempotencyKey, UUID productId, long principal)
        throws Exception {
        MvcResult result = mockMvc.perform(
                subscriptionRequest(idempotencyKey, productId, principal)
            )
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
        subscriptionRequest(UUID idempotencyKey, UUID productId, long principal) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("expected_state_version", 0);
        body.put("product_id", productId.toString());
        body.put("principal", principal);
        body.put("expected_product_version", 2);
        return post("/classrooms/{classroomId}/deposit-accounts", classroomId)
            .principal(student)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body.toString());
    }

    private JsonNode terminate(UUID idempotencyKey, UUID accountId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("expected_state_version", 0);
        body.put("command", "EARLY_TERMINATE");
        MvcResult result = mockMvc.perform(post(
                "/classrooms/{classroomId}/deposit-accounts/{accountId}/commands",
                classroomId,
                accountId
            )
                .principal(student)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void activateDepositModule() {
        ClassroomModule module = moduleRepository
            .findByClassroomIdAndModuleCode(classroomId, "DEPOSIT")
            .orElseThrow();
        module.activate(Map.of(), CREATED_AT.plusSeconds(1));
        moduleRepository.save(module);
    }

    private long walletBalance() {
        return ledgerAccountRepository
            .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                classroomId,
                studentId,
                LedgerAccountType.STUDENT_WALLET,
                LedgerAccountStatus.ACTIVE
            )
            .orElseThrow()
            .getCurrentBalance();
    }

    private long liabilityBalance() {
        return ledgerAccountRepository
            .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                classroomId,
                "DEPOSIT_LIABILITY",
                LedgerAccountType.DEPOSIT_LIABILITY,
                LedgerAccountStatus.ACTIVE
            )
            .orElseThrow()
            .getCurrentBalance();
    }

    private int depositAccountCount() {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM deposit_accounts WHERE classroom_id = ?",
            Integer.class,
            classroomId
        );
    }

    private static String randomClassroomCode() {
        String value = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return value.substring(0, 8).replace('I', 'J').replace('O', 'P').replace('L', 'M');
    }
}
