package com.b201.ecovillage.ai.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.ai.application.BalancingFallbackFactory;
import com.b201.ecovillage.ai.application.OperationalAnalysisCommand;
import com.b201.ecovillage.ai.application.OperationalAnalysisResult;
import com.b201.ecovillage.ai.application.OperationalAnalysisService;
import com.b201.ecovillage.ai.contract.BalancingContract;
import com.b201.ecovillage.ai.infrastructure.BalancingAiClient;
import com.b201.ecovillage.ai.repository.AiDecisionRepository;
import com.b201.ecovillage.ai.repository.AiProposalRepository;
import com.b201.ecovillage.ai.repository.AiRunRepository;
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
import com.b201.ecovillage.ledger.domain.LedgerActorType;
import com.b201.ecovillage.ledger.domain.LedgerEntry;
import com.b201.ecovillage.ledger.domain.TransactionGroup;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ledger.repository.LedgerEntryRepository;
import com.b201.ecovillage.ledger.repository.TransactionGroupRepository;
import com.b201.ecovillage.store.domain.StoreProduct;
import com.b201.ecovillage.store.domain.StoreProductKind;
import com.b201.ecovillage.store.domain.StoreProductVersion;
import com.b201.ecovillage.store.domain.StoreStock;
import com.b201.ecovillage.store.repository.StoreProductRepository;
import com.b201.ecovillage.store.repository.StoreProductVersionRepository;
import com.b201.ecovillage.store.repository.StoreStockRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ai-operational-execution;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "ecovillage.ai-monitor.enabled=false"
})
@AutoConfigureMockMvc
@Import(AiOperationalAnalysisApiTest.FakeAiConfiguration.class)
@Transactional
class AiOperationalAnalysisApiTest {

    private static final String OWNER_SUBJECT = "operational-ai-owner";
    private static final Instant NOW = Instant.parse("2026-08-01T01:00:00Z");
    private static final Instant P0_FROM = Instant.parse("2026-07-10T15:00:00Z");
    private static final Instant P1_FROM = Instant.parse("2026-07-17T15:00:00Z");
    private static final Instant P2_FROM = Instant.parse("2026-07-24T15:00:00Z");
    private static final Instant P2_TO = Instant.parse("2026-07-31T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomStudentRepository studentRepository;

    @Autowired
    private ClassroomTeacherRepository teacherRepository;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private LedgerAccountRepository accountRepository;

    @Autowired
    private TransactionGroupRepository groupRepository;

    @Autowired
    private LedgerEntryRepository entryRepository;

    @Autowired
    private StoreProductRepository productRepository;

    @Autowired
    private StoreProductVersionRepository productVersionRepository;

    @Autowired
    private StoreStockRepository stockRepository;

    @Autowired
    private OperationalAnalysisService analysisService;

    @Autowired
    private AiRunRepository runRepository;

    @Autowired
    private AiProposalRepository proposalRepository;

    @Autowired
    private AiDecisionRepository decisionRepository;

    @Autowired
    private CountingBalancingAiClient aiClient;

    private UUID classroomId;
    private UUID teacherId;
    private Principal owner;
    private List<LedgerAccount> wallets;

    @BeforeEach
    void setUp() {
        aiClient.calls.set(0);
        classroomId = UUID.randomUUID();
        teacherId = UUID.randomUUID();
        owner = () -> OWNER_SUBJECT;
        userRepository.save(UserAccount.createTeacher(
            teacherId,
            OWNER_SUBJECT,
            "Operational AI Owner",
            NOW
        ));
        classroomRepository.save(Classroom.create(
            classroomId,
            UUID.randomUUID(),
            randomClassroomCode(),
            "ECO",
            5,
            CourseSize.ONE_MONTH,
            5,
            LocalDate.parse("2026-07-04"),
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
        wallets = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            UUID studentId = UUID.randomUUID();
            studentRepository.save(ClassroomStudent.enroll(
                studentId,
                classroomId,
                index,
                "학생" + index,
                P0_FROM.minusSeconds(86_400)
            ));
            LedgerAccount wallet = LedgerAccount.studentWallet(
                UUID.randomUUID(),
                classroomId,
                studentId,
                P0_FROM.minusSeconds(86_400)
            );
            wallet = accountRepository.save(wallet);
            wallets.add(wallet);
            ledgerPost(wallet, "SALARY_PAYOUT", 1000, P0_FROM.minusSeconds(3_600));
        }
        createProduct();
    }

    @Test
    void onDemandAggregatesTwoPeriodsAndReplaysTheSameRequestWithoutAnotherAiCall()
        throws Exception {
        addPurchases(P1_FROM, P2_FROM);
        addPurchases(P2_FROM, P2_TO);
        entityManager.flush();
        UUID requestId = UUID.randomUUID();

        MvcResult first = mockMvc.perform(post(
                "/classrooms/{classroomId}/ai/balancing-analysis-operations",
                classroomId
            )
                .principal(owner)
                .header("Idempotency-Key", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(onDemandBody(P2_FROM, P2_TO)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.analysis_mode").value("ON_DEMAND"))
            .andExpect(jsonPath("$.data.analysis_severity").value("WARN"))
            .andExpect(jsonPath("$.data.sample.active_students").value(5))
            .andExpect(jsonPath("$.data.sample.related_transactions").value(10))
            .andExpect(jsonPath("$.data.metrics[0].state").value("WARN"))
            .andExpect(jsonPath("$.data.metrics[0].direction").value("LOW"))
            .andExpect(jsonPath("$.data.proposal_id").isNotEmpty())
            .andReturn();
        String aiRunId = objectMapper.readTree(first.getResponse().getContentAsString())
            .path("data")
            .path("ai_run_id")
            .asText();

        mockMvc.perform(post(
                "/classrooms/{classroomId}/ai/balancing-analysis-operations",
                classroomId
            )
                .principal(owner)
                .header("Idempotency-Key", requestId)
                .header("X-Correlation-Id", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(onDemandBody(P2_FROM, P2_TO)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.ai_run_id").value(aiRunId));

        assertEquals(1, aiClient.calls.get());
        assertEquals(1, runRepository.count());
        assertEquals(1, proposalRepository.count());
    }

    @Test
    void insufficientDataAndAutoNormalFinishWithoutCallingFastApi() throws Exception {
        entityManager.flush();

        mockMvc.perform(post(
                "/classrooms/{classroomId}/ai/balancing-analysis-operations",
                classroomId
            )
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(onDemandBody(P2_FROM, P2_TO)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.run_status").value("INSUFFICIENT_DATA"))
            .andExpect(jsonPath("$.data.insufficient_codes[0]").value("RELATED_TRANSACTIONS_LT_10"));

        addPurchases(P2_FROM, P2_TO);
        entityManager.flush();
        OperationalAnalysisResult normal = analysisService.executeSystem(command(
            UUID.randomUUID(),
            P2_FROM,
            P2_TO,
            BalancingContract.MetricCode.ISSUANCE_AMOUNT
        ));

        assertEquals("COMPLETED", normal.runStatus());
        assertEquals(BalancingContract.AnalysisSeverity.NORMAL, normal.analysisSeverity());
        assertEquals(0, aiClient.calls.get());
    }

    @Test
    void acknowledgedAlertCanRecurInTheNextPeriodWhileEachPeriodRemainsIdempotent()
        throws Exception {
        addPurchases(P0_FROM, P1_FROM);
        addPurchases(P1_FROM, P2_FROM);
        addPurchases(P2_FROM, P2_TO);
        entityManager.flush();

        OperationalAnalysisResult first = analysisService.executeSystem(command(
            UUID.randomUUID(),
            P1_FROM,
            P2_FROM,
            BalancingContract.MetricCode.LIQUID_MONEY_CHANGE_RATE_BPS
        ));
        mockMvc.perform(post(
                "/classrooms/{classroomId}/ai/balancing-proposals/{proposalId}/commands",
                classroomId,
                first.proposalId()
            )
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "command": "ACKNOWLEDGE",
                      "reason": "첫 구간 알림을 확인했습니다."
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.decision_status").value("ACKNOWLEDGED"));

        UUID secondRequestId = UUID.randomUUID();
        OperationalAnalysisCommand secondCommand = command(
            secondRequestId,
            P2_FROM,
            P2_TO,
            BalancingContract.MetricCode.LIQUID_MONEY_CHANGE_RATE_BPS
        );
        OperationalAnalysisResult second = analysisService.executeSystem(secondCommand);
        OperationalAnalysisResult replay = analysisService.executeSystem(secondCommand);

        assertNotEquals(first.proposalId(), second.proposalId());
        assertEquals(second.aiRunId(), replay.aiRunId());
        assertEquals(2, aiClient.calls.get());
        assertEquals(2, proposalRepository.count());
        assertEquals(1, decisionRepository.count());
    }

    private OperationalAnalysisCommand command(
        UUID requestId,
        Instant from,
        Instant to,
        BalancingContract.MetricCode metric
    ) {
        return new OperationalAnalysisCommand(
            null,
            classroomId,
            requestId,
            UUID.randomUUID(),
            BalancingContract.AnalysisMode.AUTO_MONITOR,
            from,
            to,
            Set.of(metric)
        );
    }

    private String onDemandBody(Instant from, Instant to) {
        return """
            {
              "analysis_mode": "ON_DEMAND",
              "period": {
                "from": "%s",
                "to": "%s"
              },
              "metric_scopes": ["LIQUID_MONEY_CHANGE_RATE_BPS"]
            }
            """.formatted(from, to);
    }

    private void addPurchases(Instant from, Instant to) {
        long spacing = (to.getEpochSecond() - from.getEpochSecond()) / 11;
        for (int index = 0; index < 10; index++) {
            ledgerPost(
                wallets.get(index % wallets.size()),
                "STORE_PURCHASE",
                -100,
                from.plusSeconds(spacing * (index + 1))
            );
        }
    }

    private void ledgerPost(
        LedgerAccount wallet,
        String transactionType,
        long amount,
        Instant occurredAt
    ) {
        UUID groupId = UUID.randomUUID();
        TransactionGroup group = TransactionGroup.receive(
            groupId,
            classroomId,
            transactionType,
            LedgerActorType.SYSTEM,
            null,
            null,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            transactionType,
            occurredAt
        );
        group.startValidation(occurredAt);
        group.commit(occurredAt);
        groupRepository.save(group);
        entryRepository.saveAll(List.of(LedgerEntry.post(
            UUID.randomUUID(),
            classroomId,
            groupId,
            wallet.getId(),
            wallet.post(amount, occurredAt),
            occurredAt
        )));
        accountRepository.save(wallet);
    }

    private void createProduct() {
        UUID productId = UUID.randomUUID();
        StoreProduct product = StoreProduct.draft(
            productId,
            classroomId,
            "학급 상품",
            "운영 분석 테스트 상품",
            StoreProductKind.PHYSICAL,
            teacherId,
            P0_FROM.minusSeconds(86_400)
        );
        product.publish(P0_FROM.minusSeconds(86_300));
        productRepository.save(product);
        productVersionRepository.save(StoreProductVersion.publish(
            UUID.randomUUID(),
            classroomId,
            productId,
            1,
            StoreProductKind.PHYSICAL,
            100,
            0,
            true,
            java.util.Map.of(),
            P0_FROM.minusSeconds(86_300)
        ));
        stockRepository.save(StoreStock.initialize(
            productId,
            classroomId,
            100,
            P0_FROM.minusSeconds(86_300)
        ));
    }

    private static String randomClassroomCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    static class CountingBalancingAiClient implements BalancingAiClient {

        private final BalancingFallbackFactory fallbackFactory;
        private final AtomicInteger calls = new AtomicInteger();

        CountingBalancingAiClient(BalancingFallbackFactory fallbackFactory) {
            this.fallbackFactory = fallbackFactory;
        }

        @Override
        public BalancingContract.Response explain(BalancingContract.Request request) {
            calls.incrementAndGet();
            BalancingContract.Response fallback = fallbackFactory.create(request, "FAKE_PROVIDER");
            return new BalancingContract.Response(
                fallback.aiRunId(),
                fallback.schemaVersion(),
                fallback.promptVersion(),
                "FAKE",
                "fake-balancing-model",
                fallback.safetyStatus(),
                List.of(),
                fallback.usage(),
                fallback.output()
            );
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeAiConfiguration {

        @Bean
        @Primary
        Clock fixedOperationalClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        CountingBalancingAiClient countingBalancingAiClient(
            BalancingFallbackFactory fallbackFactory
        ) {
            return new CountingBalancingAiClient(fallbackFactory);
        }
    }
}
