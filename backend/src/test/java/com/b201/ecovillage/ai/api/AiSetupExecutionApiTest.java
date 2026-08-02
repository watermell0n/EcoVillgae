package com.b201.ecovillage.ai.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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

import com.b201.ecovillage.ai.application.SetupPrecheckFallbackFactory;
import com.b201.ecovillage.ai.contract.SetupPrecheckContract;
import com.b201.ecovillage.ai.infrastructure.AiServiceCallException;
import com.b201.ecovillage.ai.infrastructure.SetupPrecheckAiClient;
import com.b201.ecovillage.ai.repository.AiArtifactRepository;
import com.b201.ecovillage.ai.repository.AiDecisionRepository;
import com.b201.ecovillage.ai.repository.AiProposalRepository;
import com.b201.ecovillage.ai.repository.AiRunRepository;
import com.b201.ecovillage.ai.repository.AsyncOperationRepository;
import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomTeacher;
import com.b201.ecovillage.identity.domain.ClassroomTeacherRole;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomTeacherRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ai-setup-execution;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Import(AiSetupExecutionApiTest.FakeAiConfiguration.class)
@Transactional
class AiSetupExecutionApiTest {

    private static final String OWNER_SUBJECT = "ai-owner";
    private static final Instant NOW = Instant.parse("2026-08-01T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private AiRunRepository runRepository;

    @Autowired
    private AiArtifactRepository artifactRepository;

    @Autowired
    private AiProposalRepository proposalRepository;

    @Autowired
    private AiDecisionRepository decisionRepository;

    @Autowired
    private AsyncOperationRepository operationRepository;

    @Autowired
    private ControllableAiClient aiClient;

    private UUID classroomId;
    private UUID moduleId;
    private Principal owner;

    @BeforeEach
    void setUp() {
        aiClient.mode = Mode.SUCCESS;
        aiClient.calls.set(0);
        classroomId = UUID.randomUUID();
        moduleId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        owner = () -> OWNER_SUBJECT;
        userRepository.save(UserAccount.createTeacher(
            teacherId,
            OWNER_SUBJECT,
            "AI Owner",
            NOW
        ));
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
            moduleId,
            classroomId,
            "DEPOSIT",
            NOW
        ));
    }

    @Test
    void storesValidatedExplanationAndAppliesProposalThroughModuleTransaction()
        throws Exception {
        UUID analysisRequestId = UUID.randomUUID();
        MvcResult analysis = mockMvc.perform(post(
                "/classrooms/{classroomId}/ai/balancing-analysis-operations",
                classroomId
            )
                .principal(owner)
                .header("Idempotency-Key", analysisRequestId)
                .header("X-Correlation-Id", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(moduleAnalysisBody()))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.operation_status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.run_status").value("AWAITING_TEACHER"))
            .andExpect(jsonPath("$.data.fallback_used").value(false))
            .andExpect(jsonPath("$.data.overall_severity").value("PASS"))
            .andExpect(jsonPath("$.data.candidates[0].proposed_value").value(300))
            .andReturn();

        JsonNode data = objectMapper.readTree(analysis.getResponse().getContentAsString()).path("data");
        UUID proposalId = UUID.fromString(data.path("proposal_id").asText());
        assertEquals(1, operationRepository.count());
        assertEquals(1, runRepository.count());
        assertEquals(1, artifactRepository.count());
        assertEquals(1, proposalRepository.count());
        assertEquals(1, aiClient.calls.get());

        mockMvc.perform(post(
                "/classrooms/{classroomId}/ai/balancing-analysis-operations",
                classroomId
            )
                .principal(owner)
                .header("Idempotency-Key", analysisRequestId)
                .header("X-Correlation-Id", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(moduleAnalysisBody()))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.ai_run_id").value(data.path("ai_run_id").asText()));

        assertEquals(1, aiClient.calls.get());
        assertEquals(1, runRepository.count());
        assertEquals("DISABLED", moduleRepository.findById(moduleId).orElseThrow().getStatus());
        assertEquals(0, policyRepository.count());

        mockMvc.perform(post(
                "/classrooms/{classroomId}/ai/balancing-proposals/{proposalId}/commands",
                classroomId,
                proposalId
            )
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "command": "ACKNOWLEDGE",
                      "reason": "추천 내용을 먼저 확인했습니다."
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.decision_status").value("ACKNOWLEDGED"));

        assertEquals("DISABLED", moduleRepository.findById(moduleId).orElseThrow().getStatus());
        assertEquals(0, policyRepository.count());

        mockMvc.perform(post(
                "/classrooms/{classroomId}/ai/balancing-proposals/{proposalId}/commands",
                classroomId,
                proposalId
            )
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .header("X-Correlation-Id", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "command": "APPLY",
                      "edited_values": [],
                      "reason": "추천 금리를 확인했습니다.",
                      "expected_policy_version": 0
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.decision_status").value("APPLIED"))
            .andExpect(jsonPath("$.data.policy_version").value(1))
            .andExpect(jsonPath("$.data.applied_values[0].proposed_value").value(300));

        assertEquals("ACTIVE", moduleRepository.findById(moduleId).orElseThrow().getStatus());
        assertEquals(1, policyRepository.count());
        assertEquals(2, decisionRepository.count());
        assertEquals("COMPLETED", runRepository.findAll().getFirst().getStatus());
        assertEquals("APPLIED", proposalRepository.findById(proposalId).orElseThrow().getStatus());
    }

    @Test
    void fallsBackToRuleResultWhenFastApiFails() throws Exception {
        aiClient.mode = Mode.FAIL;

        mockMvc.perform(post(
                "/classrooms/{classroomId}/ai/balancing-analysis-operations",
                classroomId
            )
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(moduleAnalysisBody()))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.fallback_used").value(true))
            .andExpect(jsonPath("$.data.fallback_code").value("AI_TIMEOUT"))
            .andExpect(jsonPath("$.data.candidates[0].proposed_value").value(300));

        assertEquals(1, artifactRepository.count());
        assertEquals(1, proposalRepository.count());
    }

    @Test
    void rejectsChangedAiNumberAndUsesSpringFallback() throws Exception {
        aiClient.mode = Mode.TAMPER;

        mockMvc.perform(post(
                "/classrooms/{classroomId}/ai/balancing-analysis-operations",
                classroomId
            )
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(moduleAnalysisBody()))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.fallback_used").value(true))
            .andExpect(jsonPath("$.data.fallback_code").value("AI_OUTPUT_INVALID"))
            .andExpect(jsonPath("$.data.candidates[0].proposed_value").value(300));
    }

    @Test
    void blocksApplyWhenCurrentPolicyVersionChangedAfterAnalysis() throws Exception {
        MvcResult analysis = mockMvc.perform(post(
                "/classrooms/{classroomId}/ai/balancing-analysis-operations",
                classroomId
            )
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(moduleAnalysisBody()))
            .andExpect(status().isAccepted())
            .andReturn();
        UUID proposalId = UUID.fromString(
            objectMapper.readTree(analysis.getResponse().getContentAsString())
                .path("data")
                .path("proposal_id")
                .asText()
        );
        policyRepository.save(ClassroomPolicyVersion.policy(
            UUID.randomUUID(),
            classroomId,
            "DEPOSIT_RATE",
            1,
            java.util.Map.of("rate_bps", 250),
            "TEACHER",
            NOW.plusSeconds(60)
        ));

        mockMvc.perform(post(
                "/classrooms/{classroomId}/ai/balancing-proposals/{proposalId}/commands",
                classroomId,
                proposalId
            )
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "command": "APPLY",
                      "reason": "예전 제안을 적용합니다.",
                      "expected_policy_version": 0
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("POLICY_VERSION_CONFLICT"));

        assertEquals("DISABLED", moduleRepository.findById(moduleId).orElseThrow().getStatus());
        assertEquals(0, decisionRepository.count());
    }

    @Test
    void rejectsApplyForUnsupportedSetupProposalWithoutSideEffects() throws Exception {
        MvcResult analysis = mockMvc.perform(post(
                "/classrooms/{classroomId}/ai/balancing-analysis-operations",
                classroomId
            )
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(moduleAnalysisBody()))
            .andExpect(status().isAccepted())
            .andReturn();
        UUID proposalId = UUID.fromString(
            objectMapper.readTree(analysis.getResponse().getContentAsString())
                .path("data")
                .path("proposal_id")
                .asText()
        );
        var proposal = proposalRepository.findById(proposalId).orElseThrow();
        proposal.replaceProposedValues(Map.of(
            "context_type", "PRODUCT_DRAFT",
            "unit_price", 300
        ), NOW.plusSeconds(1));
        proposalRepository.saveAndFlush(proposal);

        mockMvc.perform(post(
                "/classrooms/{classroomId}/ai/balancing-proposals/{proposalId}/commands",
                classroomId,
                proposalId
            )
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "command": "APPLY",
                      "reason": "지원되지 않는 대상의 적용 차단을 확인합니다.",
                      "expected_policy_version": 0
                    }
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("INVALID_RECOMMENDATION_CONTEXT"));

        assertEquals("DISABLED", moduleRepository.findById(moduleId).orElseThrow().getStatus());
        assertEquals(0, policyRepository.count());
        assertEquals(0, decisionRepository.count());
        assertEquals(
            "AWAITING_TEACHER",
            proposalRepository.findById(proposalId).orElseThrow().getStatus()
        );
    }

    private String moduleAnalysisBody() {
        return """
            {
              "analysis_mode": "SETUP_PRECHECK",
              "trigger_context": {
                "context_type": "MODULE_ACTIVATION",
                "module_activation": {
                  "module_code": "DEPOSIT",
                  "target_id": "%s"
                }
              }
            }
            """.formatted(moduleId);
    }

    private static String randomClassroomCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    enum Mode {
        SUCCESS,
        FAIL,
        TAMPER
    }

    static class ControllableAiClient implements SetupPrecheckAiClient {

        private final SetupPrecheckFallbackFactory fallbackFactory;
        private final AtomicInteger calls = new AtomicInteger();
        private Mode mode = Mode.SUCCESS;

        ControllableAiClient(SetupPrecheckFallbackFactory fallbackFactory) {
            this.fallbackFactory = fallbackFactory;
        }

        @Override
        public SetupPrecheckContract.Response explain(SetupPrecheckContract.Request request) {
            calls.incrementAndGet();
            if (mode == Mode.FAIL) {
                throw new AiServiceCallException("AI_TIMEOUT", "simulated timeout");
            }
            SetupPrecheckContract.Response base = fallbackFactory.create(
                request,
                "rule-engine-v2",
                "FAKE_PROVIDER"
            );
            SetupPrecheckContract.Output output = base.output();
            if (mode == Mode.TAMPER) {
                List<SetupPrecheckContract.CandidateExplanation> changed = new ArrayList<>(
                    output.candidateExplanations()
                );
                SetupPrecheckContract.CandidateExplanation current = changed.getFirst();
                changed.set(0, new SetupPrecheckContract.CandidateExplanation(
                    current.actionId(),
                    current.targetType(),
                    current.label(),
                    current.proposedValue() + 1,
                    current.recommendedMinValue(),
                    current.recommendedMaxValue(),
                    current.allowedMinValue(),
                    current.allowedMaxValue(),
                    current.unit(),
                    current.evidenceReasonCodes(),
                    current.rationale(),
                    current.cautions()
                ));
                output = new SetupPrecheckContract.Output(
                    output.reviewStatus(),
                    output.contextType(),
                    output.overallSeverity(),
                    output.summary(),
                    changed,
                    output.checkExplanations()
                );
            }
            return new SetupPrecheckContract.Response(
                base.aiRunId(),
                base.schemaVersion(),
                base.promptVersion(),
                "FAKE",
                "fake-model",
                base.safetyStatus(),
                List.of(),
                base.usage(),
                output
            );
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeAiConfiguration {

        @Bean
        @Primary
        ControllableAiClient controllableAiClient(
            SetupPrecheckFallbackFactory fallbackFactory
        ) {
            return new ControllableAiClient(fallbackFactory);
        }
    }
}
