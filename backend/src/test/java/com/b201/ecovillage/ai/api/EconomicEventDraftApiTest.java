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

    private void activateModule(String moduleCode) {
        ClassroomModule module = ClassroomModule.disabled(
            UUID.randomUUID(),
            classroomId,
            moduleCode,
            NOW
        );
        module.activate(java.util.Map.of(), NOW);
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
    void rejectsInvestmentNewsUntilInvestmentDomainExists() throws Exception {
        createClassroom(6);

        mockMvc.perform(draftRequest("{\"template_type\": \"INVESTMENT_NEWS\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("TEMPLATE_NOT_SUPPORTED"));
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
        private EconomicEventContract.Request lastRequest;

        RecordingEventClient(EconomicEventFallbackFactory fallbackFactory) {
            this.fallbackFactory = fallbackFactory;
        }

        void reset() {
            calls = 0;
            fail = false;
            tamperTemplate = false;
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
                    List.of("진행 문구")
                )
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
