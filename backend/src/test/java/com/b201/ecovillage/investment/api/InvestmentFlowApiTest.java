package com.b201.ecovillage.investment.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.b201.ecovillage.investment.repository.InvestmentMarketRepository;
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
    @Autowired InvestmentMarketRepository marketRepository;

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

        // 하루를 넘기면 숨겨 둔 다음 가격이 공개된다. 장기는 마지막 날에만 정산하므로 아직 아니다.
        String advancedBody = mockMvc.perform(post("/classrooms/{classroomId}/investment-market/day-commands", classroomId)
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"investment_mode":"LONG_TERM","expected_state_version":%d}
                    """.formatted(marketStateVersion())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.current_step").value(2))
            .andExpect(jsonPath("$.data.market_status").value("OPEN"))
            .andExpect(jsonPath("$.data.settled").value(false))
            .andReturn().getResponse().getContentAsString();
        assertEquals(20, objectMapper.readTree(advancedBody).get("data").get("total_steps").asInt());

        // 2일차 가격은 1000의 1% 상승인 1010이다. 이 시세로 정산되어야 한다.
        mockMvc.perform(get("/classrooms/{classroomId}/investment-market", classroomId)
                .param("investment_mode", "LONG_TERM")
                .principal(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assets[0].price_history.length()").value(2))
            .andExpect(jsonPath("$.data.assets[0].current_price").value(1010));
    }

    /**
     * 교사가 "운영 관리 &gt; 투자"에서 정한 하루 변동 범위와 1주 시작가가 그대로 쓰인다.
     * 가격은 그 범위 안에서 매일 새로 뽑히므로 오를 수도 내릴 수도 있어야 한다.
     */
    @Test
    void buildsTheGeneratedPathInsideTheConfirmedReturnBand() throws Exception {
        mockMvc.perform(put("/classrooms/{classroomId}/investment-market/scenario", classroomId)
                .principal(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "investment_mode":"LONG_TERM",
                      "prefer_historical_index":false,
                      "asset_conditions":[
                        {"symbol":"GREEN","start_price":500,"min_return_bps":-300,"max_return_bps":300},
                        {"symbol":"CLOUD","start_price":620}
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.scenario_source").value("AI_GENERATED"))
            .andExpect(jsonPath("$.data.assets[0].initial_price").value(500))
            .andExpect(jsonPath("$.data.assets[0].min_return_bps").value(-300))
            .andExpect(jsonPath("$.data.assets[0].max_return_bps").value(300))
            .andExpect(jsonPath("$.data.assets[1].initial_price").value(620))
            // 보내지 않은 종목은 서버 기본 시작가와 기본 변동 범위를 쓴다.
            .andExpect(jsonPath("$.data.assets[2].initial_price").value(800));

        String body = mockMvc.perform(get("/classrooms/{classroomId}/investment-market", classroomId)
                .param("investment_mode", "LONG_TERM")
                .principal(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.assets[0].min_return_bps").value(-300))
            .andExpect(jsonPath("$.data.assets[0].planned_price_history[0].price").value(500))
            // 첫날은 시작가를 그대로 공개하므로 등락이 없다.
            .andExpect(jsonPath("$.data.assets[0].planned_price_history[0].change_rate_bps").value(0))
            .andReturn().getResponse().getContentAsString();

        JsonNode path = objectMapper.readTree(body)
            .get("data").get("assets").get(0).get("planned_price_history");
        boolean rose = false;
        boolean fell = false;
        for (JsonNode point : path) {
            int rate = point.get("change_rate_bps").asInt();
            assertTrue(rate >= -300 && rate <= 300, "등락이 정한 범위를 벗어났습니다: " + rate);
            rose = rose || rate > 0;
            fell = fell || rate < 0;
        }
        // 20단계짜리 흐름이 한쪽으로만 움직이면 학생이 판단할 이유가 없다.
        assertTrue(rose, "오르는 날이 하루도 없습니다");
        assertTrue(fell, "내리는 날이 하루도 없습니다");
    }

    /** 종목마다 다른 씨앗을 쓰므로 같은 시나리오 안에서도 흐름이 겹치지 않아야 한다. */
    @Test
    void drawsADifferentPathForEachAsset() throws Exception {
        mockMvc.perform(put("/classrooms/{classroomId}/investment-market/scenario", classroomId)
                .principal(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"investment_mode":"LONG_TERM","prefer_historical_index":false}
                    """))
            .andExpect(status().isOk());

        String body = mockMvc.perform(get("/classrooms/{classroomId}/investment-market", classroomId)
                .param("investment_mode", "LONG_TERM")
                .principal(owner))
            .andReturn().getResponse().getContentAsString();
        JsonNode assets = objectMapper.readTree(body).get("data").get("assets");
        assertEquals(4, assets.size());
        assertNotEquals(
            assets.get(0).get("planned_price_history").toString(),
            assets.get(1).get("planned_price_history").toString()
        );
    }

    @Test
    void rejectsAReturnBandOutsideTheAllowedRange() throws Exception {
        mockMvc.perform(put("/classrooms/{classroomId}/investment-market/scenario", classroomId)
                .principal(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "investment_mode":"LONG_TERM",
                      "prefer_historical_index":false,
                      "asset_conditions":[
                        {"symbol":"GREEN","min_return_bps":-9000,"max_return_bps":9000}
                      ]
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void settlesEveryHoldingAndClosesTheMarketOnTheFinalDay() throws Exception {
        mockMvc.perform(put("/classrooms/{classroomId}/investment-market/scenario", classroomId)
                .principal(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"investment_mode":"LONG_TERM","prefer_historical_index":true}
                    """))
            .andExpect(status().isOk());
        mockMvc.perform(post("/classrooms/{classroomId}/investment-market/commands", classroomId)
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"command":"START","investment_mode":"LONG_TERM","expected_state_version":%d}
                    """.formatted(marketStateVersion())))
            .andExpect(status().isOk());

        String marketBody = mockMvc.perform(get("/classrooms/{classroomId}/investment-market", classroomId)
                .param("investment_mode", "LONG_TERM")
                .principal(owner))
            .andReturn().getResponse().getContentAsString();
        JsonNode asset = objectMapper.readTree(marketBody).get("data").get("assets").get(0);
        mockMvc.perform(post("/classrooms/{classroomId}/investment-orders", classroomId)
                .principal(student)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"side":"BUY","asset_id":"%s","quantity":2,"expected_price_version":1}
                    """.formatted(asset.get("asset_id").asText())))
            .andExpect(status().isCreated());

        // 20단계 시나리오이므로 19번 넘기면 마지막 날이다. 마지막 날에 최종 정산하고 시장을 닫는다.
        String lastBody = null;
        for (int day = 2; day <= 20; day++) {
            lastBody = mockMvc.perform(post("/classrooms/{classroomId}/investment-market/day-commands", classroomId)
                    .principal(owner)
                    .header("Idempotency-Key", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"investment_mode":"LONG_TERM","expected_state_version":%d}
                        """.formatted(marketStateVersion())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        }
        JsonNode last = objectMapper.readTree(lastBody).get("data");
        assertEquals("CLOSED", last.get("market_status").asText());
        assertEquals(true, last.get("settled").asBoolean());
        assertEquals(1, last.get("settled_position_count").asInt());

        // 보유량은 비워지고 지갑에는 마지막 시세로 계산한 지급액이 들어온다.
        assertEquals(0, positionRepository.findAll().getFirst().getQuantity());
        long settled = last.get("settled_amount").asLong();
        assertEquals(8_000L + settled, ledgerAccountRepository
            .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                classroomId, studentId, LedgerAccountType.STUDENT_WALLET,
                LedgerAccountStatus.ACTIVE
            ).orElseThrow().getCurrentBalance());

        // 정산이 끝난 시장은 CLOSED이므로 같은 방식의 새 시나리오를 다시 만들 수 있다.
        mockMvc.perform(put("/classrooms/{classroomId}/investment-market/scenario", classroomId)
                .principal(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"investment_mode":"LONG_TERM","prefer_historical_index":true}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.market_status").value("DRAFT"));
    }

    @Test
    void settlesShortTermOnTheNextDayBecauseSettlementIsMandatory() throws Exception {
        ClassroomModule module = moduleRepository
            .findByClassroomIdAndModuleCode(classroomId, "INVESTMENT").orElseThrow();
        module.changeInvestmentModes(List.of("SHORT_TERM"), NOW);
        moduleRepository.saveAndFlush(module);

        // 등락은 범위 안에서 새로 뽑히므로, 정산 금액을 확인하려면 범위를 한 점으로 좁힌다.
        mockMvc.perform(put("/classrooms/{classroomId}/investment-market/scenario", classroomId)
                .principal(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "investment_mode":"SHORT_TERM",
                      "prefer_historical_index":false,
                      "asset_conditions":[
                        {"symbol":"GREEN","min_return_bps":650,"max_return_bps":650}
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            // 단기는 역사 지수를 쓰지 않고 두 단계짜리 AI 생성 흐름을 쓴다.
            .andExpect(jsonPath("$.data.scenario_source").value("AI_GENERATED"))
            .andExpect(jsonPath("$.data.step_count").value(2));
        mockMvc.perform(post("/classrooms/{classroomId}/investment-market/commands", classroomId)
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"command":"START","investment_mode":"SHORT_TERM","expected_state_version":%d}
                    """.formatted(marketStateVersion())))
            .andExpect(status().isOk());

        String marketBody = mockMvc.perform(get("/classrooms/{classroomId}/investment-market", classroomId)
                .param("investment_mode", "SHORT_TERM")
                .principal(owner))
            .andReturn().getResponse().getContentAsString();
        JsonNode asset = objectMapper.readTree(marketBody).get("data").get("assets").get(0);
        mockMvc.perform(post("/classrooms/{classroomId}/investment-orders", classroomId)
                .principal(student)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"side":"BUY","asset_id":"%s","quantity":2,"expected_price_version":1}
                    """.formatted(asset.get("asset_id").asText())))
            .andExpect(status().isCreated());

        // 하루만 넘겨도 의무 정산이 일어나고 시장이 닫힌다.
        mockMvc.perform(post("/classrooms/{classroomId}/investment-market/day-commands", classroomId)
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"investment_mode":"SHORT_TERM","expected_state_version":%d}
                    """.formatted(marketStateVersion())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.settled").value(true))
            .andExpect(jsonPath("$.data.market_status").value("CLOSED"))
            .andExpect(jsonPath("$.data.settled_position_count").value(1))
            // 단기 2단계 상승률은 650bps이므로 1000 -> 1065, 두 주면 2130을 지급한다.
            .andExpect(jsonPath("$.data.settled_amount").value(2130));

        assertEquals(0, positionRepository.findAll().getFirst().getQuantity());
        assertEquals(8_000L + 2_130L, ledgerAccountRepository
            .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                classroomId, studentId, LedgerAccountType.STUDENT_WALLET,
                LedgerAccountStatus.ACTIVE
            ).orElseThrow().getCurrentBalance());

        // 정산이 끝난 시장도 조회되어야 한다. 교사 화면이 이 상태를 보고 "다음 날 열기"를 낸다.
        mockMvc.perform(get("/classrooms/{classroomId}/investment-market", classroomId)
                .param("investment_mode", "SHORT_TERM")
                .principal(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CLOSED"));

        // 단기 한 판은 하루지만 교사는 매일 새로 열 수 있어야 한다.
        mockMvc.perform(put("/classrooms/{classroomId}/investment-market/scenario", classroomId)
                .principal(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"investment_mode":"SHORT_TERM","prefer_historical_index":false}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.market_status").value("DRAFT"));
        mockMvc.perform(get("/classrooms/{classroomId}/investment-market", classroomId)
                .param("investment_mode", "SHORT_TERM")
                .principal(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    private long marketStateVersion() {
        return marketRepository.findAll().stream()
            .filter(market -> !"CLOSED".equals(market.getStatus()))
            .findFirst().orElseThrow().getStateVersion();
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
