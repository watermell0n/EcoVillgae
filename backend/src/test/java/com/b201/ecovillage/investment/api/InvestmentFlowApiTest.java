package com.b201.ecovillage.investment.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
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
import com.b201.ecovillage.investment.application.HistoricalIndexPath;
import com.b201.ecovillage.investment.application.HistoricalIndexPathProvider;
import com.b201.ecovillage.investment.domain.HistoricalIndexReference;
import com.b201.ecovillage.investment.repository.InvestmentPositionRepository;
import com.b201.ecovillage.ledger.application.AtomicLedgerTransactionService;
import com.b201.ecovillage.ledger.application.LedgerTransactionCommand;
import com.b201.ecovillage.ledger.application.LedgerTransactionPosting;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.LedgerActorType;
import com.b201.ecovillage.ledger.domain.PolicySnapshot;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ledger.repository.PolicySnapshotRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:investment-flow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "ecovillage.ai-monitor.enabled=false"
})
@AutoConfigureMockMvc
@Import(InvestmentFlowApiTest.HistoricalPathConfiguration.class)
@Transactional
class InvestmentFlowApiTest {

    private static final Instant NOW = Instant.parse("2026-08-01T01:00:00Z");
    private static final String OWNER_SUBJECT = "investment-owner";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ClassroomRepository classroomRepository;
    @Autowired ClassroomTeacherRepository teacherRepository;
    @Autowired ClassroomStudentRepository studentRepository;
    @Autowired UserAccountRepository userRepository;
    @Autowired ClassroomModuleRepository moduleRepository;
    @Autowired LedgerAccountRepository ledgerAccountRepository;
    @Autowired PolicySnapshotRepository snapshotRepository;
    @Autowired AtomicLedgerTransactionService transactionService;
    @Autowired InvestmentPositionRepository positionRepository;

    private UUID classroomId;
    private UUID studentId;
    private Principal owner;
    private Principal student;

    @BeforeEach
    void setUp() {
        classroomId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        owner = () -> OWNER_SUBJECT;
        student = () -> studentId.toString();
        UUID teacherId = UUID.randomUUID();
        classroomRepository.save(Classroom.create(
            classroomId, UUID.randomUUID(), "INVEST01", "ECO", 6, CourseSize.ONE_MONTH, 10,
            LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-20"), "Asia/Seoul", NOW
        ));
        userRepository.save(UserAccount.createTeacher(teacherId, OWNER_SUBJECT, "투자 교사", NOW));
        teacherRepository.save(ClassroomTeacher.assign(
            UUID.randomUUID(), classroomId, teacherId, ClassroomTeacherRole.OWNER, NOW
        ));
        studentRepository.save(ClassroomStudent.enroll(studentId, classroomId, 1, "투자 학생", NOW));
        ClassroomModule module = ClassroomModule.disabled(
            UUID.randomUUID(), classroomId, "INVESTMENT", NOW
        );
        module.activate(Map.of("investment_mode", "LONG_TERM"), NOW);
        moduleRepository.save(module);
        fundWallet();
    }

    @Test
    void generatesHistoricalScenarioStartsMarketAndExecutesStudentBuy() throws Exception {
        mockMvc.perform(put("/classrooms/{classroomId}/investment-market/scenario", classroomId)
                .principal(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"investment_mode":"LONG_TERM","prefer_historical_index":true}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.scenario_source").value("HISTORICAL_INDEX"))
            .andExpect(jsonPath("$.data.assets.length()").value(4));

        // 학급이 단기와 장기를 함께 운영하므로 조회에도 방식을 지정해야 시장이 하나로 정해진다.
        String draftBody = mockMvc.perform(get("/classrooms/{classroomId}/investment-market", classroomId)
                .param("investment_mode", "LONG_TERM")
                .principal(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            // 초안은 학생에게 아직 보이지 않으므로 교사가 전체 가격 경로를 미리 검토할 수 있다.
            .andExpect(jsonPath("$.data.assets[0].planned_price_history.length()").value(20))
            .andReturn().getResponse().getContentAsString();
        JsonNode draft = objectMapper.readTree(draftBody).get("data");

        mockMvc.perform(post("/classrooms/{classroomId}/investment-market/commands", classroomId)
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"command":"START","investment_mode":"LONG_TERM","expected_state_version":%d}
                    """.formatted(draft.get("state_version").asLong())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.market_status").value("OPEN"));

        // 교사가 이야기를 승인하기 전에는 학생에게 종목이 보이지 않는다. 승인이 곧 공개다.
        mockMvc.perform(get("/classrooms/{classroomId}/investment-market", classroomId)
                .param("investment_mode", "LONG_TERM")
                .principal(student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assets.length()").value(0));

        String marketBody = mockMvc.perform(get("/classrooms/{classroomId}/investment-market", classroomId)
                .param("investment_mode", "LONG_TERM")
                .principal(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.current_step").value(1))
            .andExpect(jsonPath("$.data.assets[0].price_history.length()").value(1))
            // 시장이 열린 뒤에는 교사에게도 앞으로의 가격을 주지 않는다.
            .andExpect(jsonPath("$.data.assets[0].planned_price_history.length()").value(0))
            .andReturn().getResponse().getContentAsString();
        JsonNode asset = objectMapper.readTree(marketBody).get("data").get("assets").get(0);

        mockMvc.perform(post("/classrooms/{classroomId}/investment-orders", classroomId)
                .principal(student)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"side":"BUY","asset_id":"%s","quantity":2,"expected_price_version":1}
                    """.formatted(asset.get("asset_id").asText())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("EXECUTED"))
            .andExpect(jsonPath("$.data.position_quantity").value(2))
            .andExpect(jsonPath("$.data.total_amount").value(2000));

        assertEquals(2, positionRepository.findAll().getFirst().getQuantity());
        assertEquals(8_000L, ledgerAccountRepository
            .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                classroomId, studentId, LedgerAccountType.STUDENT_WALLET,
                LedgerAccountStatus.ACTIVE
            ).orElseThrow().getCurrentBalance());
    }

    private void fundWallet() {
        LedgerAccount wallet = ledgerAccountRepository.save(LedgerAccount.studentWallet(
            UUID.randomUUID(), classroomId, studentId, NOW
        ));
        LedgerAccount issuance = ledgerAccountRepository.save(LedgerAccount.systemAccount(
            UUID.randomUUID(), classroomId, "MONEY_ISSUANCE", LedgerAccountType.MONEY_ISSUANCE, NOW
        ));
        PolicySnapshot snapshot = snapshotRepository.save(PolicySnapshot.capture(
            UUID.randomUUID(), classroomId, UUID.randomUUID().toString(), "1",
            Map.of("amount", 10_000), "INTEGER", NOW
        ));
        transactionService.execute(LedgerTransactionCommand.original(
            classroomId, "TEST_ISSUANCE", LedgerActorType.SYSTEM, studentId, snapshot.getId(),
            "/test/investment-funding", UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            UUID.randomUUID().toString(), "test funding",
            List.of(
                new LedgerTransactionPosting(wallet.getId(), 10_000),
                new LedgerTransactionPosting(issuance.getId(), -10_000)
            ), NOW
        ));
    }

    @TestConfiguration
    static class HistoricalPathConfiguration {
        @Bean
        @Primary
        HistoricalIndexPathProvider fixedHistoricalPathProvider() {
            return requiredSteps -> Optional.of(new HistoricalIndexPath(
                new HistoricalIndexReference(
                    "SP500", "2020년 시장 급변", "FRED",
                    "https://fred.stlouisfed.org/series/SP500",
                    LocalDate.parse("2020-02-03"), LocalDate.parse("2020-04-30")
                ),
                java.util.stream.IntStream.range(0, requiredSteps)
                    .map(index -> index == 0 ? 0 : 100)
                    .boxed().toList()
            ));
        }
    }
}
