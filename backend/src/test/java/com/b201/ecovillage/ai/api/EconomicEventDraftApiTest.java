package com.b201.ecovillage.ai.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.ai.application.EconomicEventFallbackFactory;
import com.b201.ecovillage.ai.contract.EconomicEventContract;
import com.b201.ecovillage.ai.domain.EconomicEventDraft;
import com.b201.ecovillage.ai.infrastructure.AiServiceCallException;
import com.b201.ecovillage.ai.infrastructure.EconomicEventAiClient;
import com.b201.ecovillage.ai.repository.EconomicEventDraftRepository;
import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomTeacher;
import com.b201.ecovillage.identity.domain.ClassroomTeacherRole;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomTeacherRepository;
import com.b201.ecovillage.investment.domain.InvestmentAsset;
import com.b201.ecovillage.investment.domain.HistoricalIndexReference;
import com.b201.ecovillage.investment.domain.InvestmentMarket;
import com.b201.ecovillage.investment.domain.InvestmentMode;
import com.b201.ecovillage.investment.domain.InvestmentPriceStep;
import com.b201.ecovillage.investment.domain.InvestmentScenarioSource;
import com.b201.ecovillage.investment.domain.InvestmentScenarioVersion;
import com.b201.ecovillage.investment.repository.InvestmentAssetRepository;
import com.b201.ecovillage.investment.repository.InvestmentMarketRepository;
import com.b201.ecovillage.investment.repository.InvestmentPriceStepRepository;
import com.b201.ecovillage.investment.repository.InvestmentScenarioVersionRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:economic-event;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "ecovillage.ai-monitor.enabled=false"
})
@AutoConfigureMockMvc
@Import(EconomicEventDraftApiTest.FakeEventConfiguration.class)
@Transactional
class EconomicEventDraftApiTest {

    private static final String OWNER_SUBJECT = "economic-event-owner";
    private static final Instant NOW = Instant.parse("2026-08-01T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomTeacherRepository teacherRepository;

    @Autowired
    private ClassroomModuleRepository moduleRepository;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private EconomicEventDraftRepository draftRepository;

    @Autowired
    private InvestmentMarketRepository marketRepository;

    @Autowired
    private InvestmentScenarioVersionRepository scenarioRepository;

    @Autowired
    private InvestmentAssetRepository assetRepository;

    @Autowired
    private InvestmentPriceStepRepository priceStepRepository;

    @Autowired
    private RecordingEventClient aiClient;

    private UUID classroomId;
    private Principal owner;

    @BeforeEach
    void setUp() {
        aiClient.reset();
        classroomId = UUID.randomUUID();
        owner = () -> OWNER_SUBJECT;
        UUID teacherId = UUID.randomUUID();
        userRepository.save(UserAccount.createTeacher(teacherId, OWNER_SUBJECT, "이벤트 담임", NOW));
        teacherRepository.save(ClassroomTeacher.assign(
            UUID.randomUUID(),
            classroomId,
            teacherId,
            ClassroomTeacherRole.OWNER,
            NOW
        ));
    }

    private void createClassroom(int grade) {
        classroomRepository.save(Classroom.create(
            classroomId,
            UUID.randomUUID(),
            UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(),
            "ECO",
            grade,
            CourseSize.ONE_MONTH,
            5,
            LocalDate.parse("2026-07-04"),
            LocalDate.parse("2026-08-31"),
            "Asia/Seoul",
            NOW
        ));
        entityManager.flush();
    }

    /**
     * 4단계 시나리오를 깔고 앞의 {@code revealedSteps}개만 공개한다.
     *
     * @return 마지막 공개 단계의 id. 공개 단계가 없으면 null이다.
     */
    private UUID openInvestmentMarket(int revealedSteps) {
        UUID marketId = UUID.randomUUID();
        UUID scenarioId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        marketRepository.save(InvestmentMarket.open(marketId, classroomId, scenarioId, revealedSteps, NOW));
        scenarioRepository.save(InvestmentScenarioVersion.published(
            scenarioId,
            classroomId,
            marketId,
            1,
            "기본 시나리오",
            InvestmentMode.LONG_TERM,
            InvestmentScenarioSource.HISTORICAL_INDEX,
            new HistoricalIndexReference(
                "SP500",
                "급격한 시장 충격과 회복 구간",
                "Federal Reserve Bank of St. Louis FRED",
                "https://fred.stlouisfed.org/series/SP500",
                LocalDate.parse("2020-02-19"),
                LocalDate.parse("2020-08-18")
            ),
            NOW
        ));
        assetRepository.save(InvestmentAsset.active(
            assetId,
            classroomId,
            scenarioId,
            "ECOCORP",
            "에코마을 상사",
            "친환경 생활용품을 만드는 가상의 마을 기업",
            0,
            NOW
        ));
        long[] prices = {1000L, 1150L, 950L, 1300L};
        int[] rates = {0, 1500, -1739, 3684};
        UUID lastRevealed = null;
        for (int step = 1; step <= prices.length; step++) {
            UUID stepId = UUID.randomUUID();
            if (step <= revealedSteps) {
                priceStepRepository.save(InvestmentPriceStep.revealed(
                    stepId,
                    classroomId,
                    assetId,
                    step,
                    prices[step - 1],
                    rates[step - 1],
                    NOW.plusSeconds(step * 60L)
                ));
                lastRevealed = stepId;
            } else {
                priceStepRepository.save(InvestmentPriceStep.hidden(
                    stepId,
                    classroomId,
                    assetId,
                    step,
                    prices[step - 1],
                    rates[step - 1],
                    NOW
                ));
            }
        }
        entityManager.flush();
        return lastRevealed;
    }

    private void activateModule(String moduleCode) {
        activateModule(moduleCode, Map.of());
    }

    private void activateModule(String moduleCode, Map<String, Object> config) {
        ClassroomModule module = ClassroomModule.disabled(
            UUID.randomUUID(),
            classroomId,
            moduleCode,
            NOW
        );
        module.activate(config, NOW);
        moduleRepository.save(module);
        entityManager.flush();
    }

    @Test
    void createsDraftWithRuleEngineEffectAndAiNarrative() throws Exception {
        createClassroom(4);
        activateModule("STORE");

        mockMvc.perform(draftRequest("{\"template_type\": \"PRICE\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.operation_id").isNotEmpty())
            .andExpect(jsonPath("$.data.status").value("RUNNING"))
            .andExpect(jsonPath("$.data.status_url").isNotEmpty());

        entityManager.flush();
        EconomicEventDraft draft = draftRepository.findAll().get(0);
        assertEquals("DRAFT", draft.getStatus(), "초안은 항상 DRAFT로 저장한다.");
        assertEquals("TEACHER_REQUEST", draft.getSourceType());
        assertEquals("AI가 만든 이벤트 이야기입니다.", draft.getContent());
        assertEquals("PRICE", draft.getEconomicEffect().get("template_type"));
        assertNotNull(draft.getEconomicEffect().get("effect_summary"));
        assertEquals(1, aiClient.calls);
    }

    @Test
    void rejectsTemplateNotAllowedForGrade() throws Exception {
        createClassroom(4);
        activateModule("QUIZ_GAME");

        mockMvc.perform(draftRequest("{\"template_type\": \"REWARD\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("GRADE_POLICY_VIOLATION"));

        assertEquals(0, aiClient.calls);
    }

    @Test
    void rejectsLoanInterestRateForFifthGrade() throws Exception {
        createClassroom(5);
        activateModule("DEPOSIT");

        mockMvc.perform(draftRequest(
                "{\"template_type\": \"INTEREST_RATE\", \"interest_rate_scope\": \"LOAN\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("GRADE_POLICY_VIOLATION"));
    }

    @Test
    void allowsDepositInterestRateForFifthGrade() throws Exception {
        createClassroom(5);
        activateModule("DEPOSIT");

        mockMvc.perform(draftRequest(
                "{\"template_type\": \"INTEREST_RATE\", \"interest_rate_scope\": \"DEPOSIT\"}"))
            .andExpect(status().isAccepted());

        assertEquals(
            "DEPOSIT",
            aiClient.lastRequest.payload().interestRateScope().name()
        );
    }

    @Test
    void rejectsTemplateWhenRequiredModuleIsNotActive() throws Exception {
        createClassroom(4);

        mockMvc.perform(draftRequest("{\"template_type\": \"PRICE\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("MODULE_NOT_ACTIVE"));

        assertEquals(0, aiClient.calls);
    }

    @Test
    void createsInvestmentNewsBoundToTheCurrentPriceStep() throws Exception {
        createClassroom(6);
        activateModule("INVESTMENT");
        UUID currentStepId = openInvestmentMarket(3);

        mockMvc.perform(draftRequest("{\"template_type\": \"INVESTMENT_NEWS\"}"))
            .andExpect(status().isAccepted());

        entityManager.flush();
        EconomicEventDraft draft = draftRepository.findAll().get(0);
        assertEquals(currentStepId, draft.getInvestmentPriceStepId(), "현재 공개 단계에 연결한다.");

        EconomicEventContract.InvestmentNewsContext context =
            aiClient.lastRequest.payload().investmentNewsContext();
        assertNotNull(context, "투자 문맥이 AI에 전달된다.");
        assertEquals(3, context.currentStepIndex());
        assertEquals(4, context.targetStepIndex());
        assertEquals(EconomicEventContract.InvestmentTargetDirection.RISE, context.targetDirection());
        assertEquals(EconomicEventContract.InvestmentMode.LONG_TERM, context.investmentMode());
        assertEquals(
            EconomicEventContract.InvestmentScenarioSource.HISTORICAL_INDEX,
            context.scenarioSource()
        );
        assertEquals("SP500", context.historicalIndexReference().seriesCode());
        assertEquals("ECOCORP", context.assetSymbol());
        // 뉴스가 학생이 이미 본 시세와 어긋나지 않도록 1단계부터 현재까지 전부 넘긴다.
        assertEquals(
            List.of(1, 2, 3),
            context.publicPriceFacts().stream()
                .map(EconomicEventContract.InvestmentPriceFact::stepIndex)
                .toList()
        );
    }

    @Test
    void hidesUnrevealedPriceStepsFromTheAiInput() throws Exception {
        createClassroom(6);
        activateModule("INVESTMENT");
        openInvestmentMarket(2);

        mockMvc.perform(draftRequest("{\"template_type\": \"INVESTMENT_NEWS\"}"))
            .andExpect(status().isAccepted());

        EconomicEventContract.InvestmentNewsContext context =
            aiClient.lastRequest.payload().investmentNewsContext();
        // 4단계까지 미리 쌓아두었지만 공개된 2단계까지만 나가야 한다.
        assertEquals(2, context.publicPriceFacts().size(), "미공개 단계는 절대 나가지 않는다.");
        assertEquals(2, context.currentStepIndex());
    }

    @Test
    void rejectsInvestmentNewsWhenNoPriceStepIsRevealed() throws Exception {
        createClassroom(6);
        activateModule("INVESTMENT");
        openInvestmentMarket(0);

        mockMvc.perform(draftRequest("{\"template_type\": \"INVESTMENT_NEWS\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("INVALID_INVESTMENT_STEP_BINDING"));

        assertEquals(0, aiClient.calls);
    }

    @Test
    void rejectsInvestmentNewsWhenCurrentStepIsTheFinalSettlementStep() throws Exception {
        createClassroom(6);
        activateModule("INVESTMENT");
        openInvestmentMarket(4);

        mockMvc.perform(draftRequest("{\"template_type\": \"INVESTMENT_NEWS\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("INVALID_INVESTMENT_STEP_BINDING"));

        assertEquals(0, aiClient.calls);
    }

    @Test
    void rejectsScenarioThatDoesNotMatchTeacherSelectedInvestmentMode() throws Exception {
        createClassroom(6);
        activateModule("INVESTMENT", Map.of("investment_mode", "SHORT_TERM"));
        openInvestmentMarket(1);

        mockMvc.perform(draftRequest("{\"template_type\": \"INVESTMENT_NEWS\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("INVESTMENT_MODE_MISMATCH"));

        assertEquals(0, aiClient.calls);
    }

    @Test
    void rejectsInvestmentNewsForFifthGrade() throws Exception {
        createClassroom(5);
        activateModule("INVESTMENT");
        openInvestmentMarket(1);

        mockMvc.perform(draftRequest("{\"template_type\": \"INVESTMENT_NEWS\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("GRADE_POLICY_VIOLATION"));
    }

    @Test
    void keepsOneActiveNewsPerPriceStep() throws Exception {
        createClassroom(6);
        activateModule("INVESTMENT");
        openInvestmentMarket(1);

        mockMvc.perform(draftRequest("{\"template_type\": \"INVESTMENT_NEWS\"}"))
            .andExpect(status().isAccepted());
        entityManager.flush();

        mockMvc.perform(draftRequest("{\"template_type\": \"INVESTMENT_NEWS\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("DUPLICATE_EVENT_RECOMMENDATION"));

        entityManager.flush();
        assertEquals(1, draftRepository.count(), "한 단계에는 활성 뉴스가 하나만 있다.");
    }

    @Test
    void fallsBackWhenAiChangesThePriceStepBinding() throws Exception {
        createClassroom(6);
        activateModule("INVESTMENT");
        UUID currentStepId = openInvestmentMarket(1);
        aiClient.tamperBinding = true;

        mockMvc.perform(draftRequest("{\"template_type\": \"INVESTMENT_NEWS\"}"))
            .andExpect(status().isAccepted());

        entityManager.flush();
        EconomicEventDraft draft = draftRepository.findAll().get(0);
        // 자산 이름과 단계 연결을 바꾼 출력은 버리고 규칙 기반 대체안을 쓴다.
        assertEquals(true, draft.getEconomicEffect().get("fallback_used"));
        assertEquals(
            "AI_OUTPUT_CONTRACT_VIOLATION",
            draft.getEconomicEffect().get("fallback_code")
        );
        assertEquals(currentStepId, draft.getInvestmentPriceStepId(), "연결은 서버 값을 유지한다.");
    }

    @Test
    void fallsBackToRuleEffectWhenFastApiFails() throws Exception {
        createClassroom(4);
        activateModule("STORE");
        aiClient.fail = true;

        mockMvc.perform(draftRequest("{\"template_type\": \"PRICE\"}"))
            .andExpect(status().isAccepted());

        entityManager.flush();
        EconomicEventDraft draft = draftRepository.findAll().get(0);
        assertEquals("DRAFT", draft.getStatus());
        assertEquals(Boolean.TRUE, draft.getEconomicEffect().get("fallback_used"));
        assertEquals("물가 변동 안내", draft.getTitle());
    }

    @Test
    void rejectsAiOutputThatChangesTheTemplate() throws Exception {
        createClassroom(4);
        activateModule("STORE");
        aiClient.tamperTemplate = true;

        mockMvc.perform(draftRequest("{\"template_type\": \"PRICE\"}"))
            .andExpect(status().isAccepted());

        entityManager.flush();
        EconomicEventDraft draft = draftRepository.findAll().get(0);
        assertEquals("PRICE", draft.getEconomicEffect().get("template_type"),
            "변조된 출력은 버리고 규칙 엔진 템플릿을 유지해야 합니다.");
        assertEquals(Boolean.TRUE, draft.getEconomicEffect().get("fallback_used"));
        assertEquals("AI_OUTPUT_CONTRACT_VIOLATION", draft.getEconomicEffect().get("fallback_code"));
    }

    @Test
    void sendsProhibitedEffectsSoTheModelCannotInventThem() throws Exception {
        createClassroom(4);
        activateModule("STORE");

        mockMvc.perform(draftRequest("{\"template_type\": \"PRICE\"}"))
            .andExpect(status().isAccepted());

        List<String> prohibited = aiClient.lastRequest.payload().prohibitedEffects();
        assertTrue(prohibited.contains("개인 잔액과 계좌의 직접 변경"));
        assertTrue(prohibited.contains("교사 승인 없는 실행과 공개"));
        assertTrue(aiClient.lastRequest.payload().effectSummary().size() >= 1);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder draftRequest(
        String body
    ) {
        return post("/classrooms/{classroomId}/ai/economic-event-draft-operations", classroomId)
            .principal(owner)
            .header("Idempotency-Key", UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    }

    static class RecordingEventClient implements EconomicEventAiClient {

        private final EconomicEventFallbackFactory fallbackFactory;
        private int calls;
        private boolean fail;
        private boolean tamperTemplate;
        private boolean tamperBinding;
        private EconomicEventContract.Request lastRequest;

        RecordingEventClient(EconomicEventFallbackFactory fallbackFactory) {
            this.fallbackFactory = fallbackFactory;
        }

        void reset() {
            calls = 0;
            fail = false;
            tamperTemplate = false;
            tamperBinding = false;
            lastRequest = null;
        }

        @Override
        public EconomicEventContract.Response draft(EconomicEventContract.Request request) {
            calls++;
            lastRequest = request;
            if (fail) {
                throw new AiServiceCallException("AI_TIMEOUT", "simulated timeout");
            }
            EconomicEventContract.Payload payload = request.payload();
            EconomicEventContract.TemplateType template = tamperTemplate
                ? EconomicEventContract.TemplateType.PUBLIC_FUND
                : payload.templateType();
            EconomicEventContract.Response template0 = fallbackFactory.create(request, "FAKE");
            return new EconomicEventContract.Response(
                request.aiRunId(),
                template0.schemaVersion(),
                template0.promptVersion(),
                "FAKE",
                "fake-event-model",
                "PASSED",
                List.of(),
                new EconomicEventContract.Usage(10, 20),
                new EconomicEventContract.Output(
                    "REVIEW_REQUIRED",
                    template,
                    payload.sourceType(),
                    payload.sourceProposalId(),
                    payload.sourceEvidenceCodes(),
                    payload.interestRateScope(),
                    "AI가 만든 이벤트 제목",
                    "AI가 만든 이벤트 이야기입니다.",
                    List.of(
                        new EconomicEventContract.Choice("A", "첫 번째 선택", "첫 번째 결과"),
                        new EconomicEventContract.Choice("B", "두 번째 선택", "두 번째 결과")
                    ),
                    List.of("진행 문구"),
                    tamperBinding
                        ? new EconomicEventContract.InvestmentNewsBinding(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            "몰래 바꾼 종목",
                            "HACKED",
                            EconomicEventContract.InvestmentMode.SHORT_TERM,
                            EconomicEventContract.InvestmentScenarioSource.AI_GENERATED,
                            EconomicEventContract.InvestmentSettlementRule.NEXT_DAY_MANDATORY,
                            99,
                            100
                        )
                        : binding(payload.investmentNewsContext()),
                    payload.investmentNewsContext() == null ? null : "교사용 설명입니다."
                )
            );
        }

        private static EconomicEventContract.InvestmentNewsBinding binding(
            EconomicEventContract.InvestmentNewsContext context
        ) {
            if (context == null) {
                return null;
            }
            return new EconomicEventContract.InvestmentNewsBinding(
                context.scenarioVersionId(),
                context.assetId(),
                context.priceStepId(),
                context.targetPriceStepId(),
                context.assetName(),
                context.assetSymbol(),
                context.investmentMode(),
                context.scenarioSource(),
                context.settlementRule(),
                context.currentStepIndex(),
                context.targetStepIndex()
            );
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeEventConfiguration {

        @Bean
        @Primary
        Clock fixedEventClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        RecordingEventClient recordingEventClient(EconomicEventFallbackFactory fallbackFactory) {
            return new RecordingEventClient(fallbackFactory);
        }
    }
}
