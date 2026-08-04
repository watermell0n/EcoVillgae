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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import com.b201.ecovillage.ai.application.SetupPrecheckFallbackFactory;
import com.b201.ecovillage.ai.contract.SetupPrecheckContract;
import com.b201.ecovillage.ai.infrastructure.AiServiceCallException;
import com.b201.ecovillage.ai.infrastructure.SetupPrecheckAiClient;
import com.b201.ecovillage.ai.repository.AiArtifactRepository;
import com.b201.ecovillage.ai.repository.AiDecisionRepository;
import com.b201.ecovillage.ai.repository.AiProposalRepository;
import com.b201.ecovillage.ai.domain.AiRun;
import com.b201.ecovillage.ai.repository.AiRunRepository;
import com.b201.ecovillage.ai.repository.AsyncOperationRepository;
import com.b201.ecovillage.audit.domain.AuditEvent;
import com.b201.ecovillage.audit.repository.AuditEventRepository;
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
import com.b201.ecovillage.quiz.domain.QuizContentType;
import com.b201.ecovillage.quiz.domain.QuizMode;
import com.b201.ecovillage.quiz.domain.QuizSession;
import com.b201.ecovillage.quiz.repository.QuizSessionRepository;
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
    private StoreProductRepository productRepository;

    @Autowired
    private StoreProductVersionRepository productVersionRepository;

    @Autowired
    private StoreStockRepository stockRepository;

    @Autowired
    private QuizSessionRepository quizSessionRepository;

    @Autowired
    private AuditEventRepository auditRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private ControllableAiClient aiClient;

    private UUID classroomId;
    private UUID moduleId;
    private UUID teacherId;
    private Principal owner;

    @BeforeEach
    void setUp() {
        aiClient.mode = Mode.SUCCESS;
        aiClient.calls.set(0);
        classroomId = UUID.randomUUID();
        moduleId = UUID.randomUUID();
        teacherId = UUID.randomUUID();
        owner = () -> OWNER_SUBJECT + "-" + classroomId;
        userRepository.save(UserAccount.createTeacher(
            teacherId,
            owner.getName(),
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
        AiRun run = runRepository.findAll().getFirst();
        assertEquals("AWAITING_TEACHER", run.getStatus());
        assertEquals("SPRING_FALLBACK", run.getModelProvider());
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
    void blocksApplyWhenExpectedPolicyVersionIsMissing() throws Exception {
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

        // 호출자가 정책 버전을 생략하면 낙관적 잠금을 건너뛰게 되므로 적용을 거절합니다.
        // 화면은 modules-and-policies의 policy_versions 값을 그대로 되돌려 보내야 합니다.
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
                      "reason": "정책 버전을 보내지 않고 적용합니다.",
                      "expected_policy_version": null
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

    @Test
    void appliesProductPriceOnceAndAuditsOriginalAndEditedValues() throws Exception {
        UUID productId = UUID.randomUUID();
        productRepository.save(StoreProduct.draft(
            productId,
            classroomId,
            "Notebook",
            "Draft product",
            StoreProductKind.PHYSICAL,
            teacherId,
            NOW
        ));
        productVersionRepository.save(StoreProductVersion.publish(
            UUID.randomUUID(),
            classroomId,
            productId,
            1,
            StoreProductKind.PHYSICAL,
            100,
            0,
            false,
            Map.of(),
            NOW
        ));
        stockRepository.save(StoreStock.initialize(productId, classroomId, 10, NOW));

        MvcResult analysis = analyze(productAnalysisBody(productId));
        JsonNode analysisData = objectMapper.readTree(
            analysis.getResponse().getContentAsString()
        ).path("data");
        UUID proposalId = UUID.fromString(analysisData.path("proposal_id").asText());
        long proposedPrice = analysisData.path("candidates").get(0)
            .path("proposed_value").asLong();
        UUID applyRequestId = UUID.randomUUID();

        String applyBody = """
            {
              "command": "APPLY",
              "edited_values": [
                { "action_id": "product-price", "proposed_value": %d }
              ],
              "reason": "Apply the reviewed product price.",
              "expected_policy_version": 0
            }
            """.formatted(proposedPrice);
        apply(proposalId, applyRequestId, applyBody)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.decision_status").value("APPLIED"))
            .andExpect(jsonPath("$.data.applied_values[0].proposed_value").value(proposedPrice));
        apply(proposalId, applyRequestId, applyBody)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.decision_status").value("APPLIED"));

        StoreProductVersion latest = productVersionRepository
            .findTopByClassroomIdAndStoreProductIdAndRetiredAtIsNullOrderByVersionDesc(
                classroomId,
                productId
            )
            .orElseThrow();
        assertEquals(2, latest.getVersion());
        assertEquals(proposedPrice, latest.getUnitPrice());
        List<AuditEvent> audits = auditRepository
            .findAllByClassroomIdOrderByOccurredAtDescIdDesc(
                classroomId,
                org.springframework.data.domain.PageRequest.of(0, 20)
            );
        AuditEvent applyAudit = audits.stream()
            .filter(event -> "AI_PROPOSAL_APPLY".equals(event.getAction()))
            .findFirst()
            .orElseThrow();
        assertTrue(applyAudit.getBeforeSummary().containsKey("original_values"));
        assertTrue(applyAudit.getAfterSummary().containsKey("edited_values"));
        assertTrue(applyAudit.getAfterSummary().containsKey("applied_values"));
    }

    @Test
    void appliesGameRewardOnceAndRejectsOutOfRangeEdit() throws Exception {
        UUID sessionId = UUID.randomUUID();
        quizSessionRepository.save(QuizSession.open(
            sessionId,
            classroomId,
            QuizMode.GAME,
            QuizContentType.ECONOMY,
            Map.of("reward_per_person", 0),
            false,
            NOW
        ));

        MvcResult analysis = analyze(quizAnalysisBody(sessionId));
        JsonNode analysisData = objectMapper.readTree(
            analysis.getResponse().getContentAsString()
        ).path("data");
        UUID proposalId = UUID.fromString(analysisData.path("proposal_id").asText());
        long proposedReward = analysisData.path("candidates").get(0)
            .path("proposed_value").asLong();
        long stateVersionBeforeApply = quizSessionRepository.findById(sessionId)
            .orElseThrow()
            .getStateVersion();
        Map<String, Object> range = proposalRepository.findById(proposalId)
            .orElseThrow()
            .getAllowedRanges();
        @SuppressWarnings("unchecked")
        long allowedMaximum = ((Number) ((List<Map<String, Object>>) range.get("items"))
            .getFirst()
            .get("allowed_max_value")).longValue();

        apply(proposalId, UUID.randomUUID(), """
            {
              "command": "APPLY",
              "edited_values": [
                { "action_id": "game-reward", "proposed_value": %d }
              ],
              "reason": "Reject an excessive reward.",
              "expected_policy_version": 0
            }
            """.formatted(allowedMaximum + 1))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("POLICY_VIOLATION"));

        UUID applyRequestId = UUID.randomUUID();
        String applyBody = """
            {
              "command": "APPLY",
              "edited_values": [],
              "reason": "Apply the reviewed GAME reward.",
              "expected_policy_version": 0
            }
            """;
        apply(proposalId, applyRequestId, applyBody)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.decision_status").value("APPLIED"));
        entityManager.flush();
        entityManager.clear();
        long stateVersionAfterApply = quizSessionRepository.findById(sessionId)
            .orElseThrow()
            .getStateVersion();
        apply(proposalId, applyRequestId, applyBody)
            .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        QuizSession applied = quizSessionRepository.findById(sessionId).orElseThrow();
        assertEquals(proposedReward, ((Number) applied.getRewardPolicyPublic()
            .get("reward_per_person")).longValue());
        assertTrue(stateVersionAfterApply > stateVersionBeforeApply);
        assertEquals(stateVersionAfterApply, applied.getStateVersion());
    }

    @Test
    void rejectsBlockedExpiredWrongTargetAndStaleProductProposals() throws Exception {
        UUID blockedProductId = createProduct("Blocked product", 2000);
        MvcResult blockedAnalysis = analyze(
            productAnalysisBody(blockedProductId).replace(
                "\"confirmed_price\": 100",
                "\"confirmed_price\": 2000"
            )
        );
        JsonNode blockedData = objectMapper.readTree(
            blockedAnalysis.getResponse().getContentAsString()
        ).path("data");
        assertEquals("BLOCK", blockedData.path("overall_severity").asText());
        UUID blockedProposalId = UUID.fromString(blockedData.path("proposal_id").asText());
        apply(blockedProposalId, UUID.randomUUID(), applyWithoutEdits("blocked"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("POLICY_VIOLATION"));

        UUID expiredProductId = createProduct("Expired product");
        UUID expiredProposalId = proposalId(analyze(productAnalysisBody(expiredProductId)));
        entityManager.createQuery("""
            update AiProposal proposal
               set proposal.expiresAt = :expired
             where proposal.id = :proposalId
            """)
            .setParameter("expired", Instant.EPOCH)
            .setParameter("proposalId", expiredProposalId)
            .executeUpdate();
        entityManager.clear();
        apply(expiredProposalId, UUID.randomUUID(), applyWithoutEdits("expired"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("PROPOSAL_EXPIRED"));

        UUID wrongTargetProductId = createProduct("Wrong target product");
        UUID wrongTargetProposalId = proposalId(
            analyze(productAnalysisBody(wrongTargetProductId))
        );
        var wrongTargetProposal = proposalRepository.findById(wrongTargetProposalId)
            .orElseThrow();
        Map<String, Object> wrongTargetValues = new java.util.LinkedHashMap<>(
            wrongTargetProposal.getProposedValues()
        );
        wrongTargetValues.put("target_id", UUID.randomUUID().toString());
        wrongTargetProposal.replaceProposedValues(wrongTargetValues, NOW.plusSeconds(1));
        proposalRepository.saveAndFlush(wrongTargetProposal);
        apply(wrongTargetProposalId, UUID.randomUUID(), applyWithoutEdits("wrong target"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("INVALID_RECOMMENDATION_CONTEXT"));

        UUID staleProductId = createProduct("Stale product");
        UUID staleProposalId = proposalId(analyze(productAnalysisBody(staleProductId)));
        StoreProduct staleProduct = productRepository.findById(staleProductId).orElseThrow();
        staleProduct.updateDescription("Changed after analysis", "stale", NOW.plusSeconds(2));
        productRepository.saveAndFlush(staleProduct);
        apply(staleProposalId, UUID.randomUUID(), applyWithoutEdits("stale"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("STALE_PROPOSAL"));
    }

    @Test
    void testQuizRewardProducesNoApplicableProposal() throws Exception {
        UUID sessionId = UUID.randomUUID();
        quizSessionRepository.save(QuizSession.open(
            sessionId,
            classroomId,
            QuizMode.TEST,
            QuizContentType.ECONOMY,
            Map.of(),
            false,
            NOW
        ));
        String body = quizAnalysisBody(sessionId)
            .replace("\"mode\": \"GAME\"", "\"mode\": \"TEST\"")
            .replace(
                "\"confirmed_reward_per_person\": 0",
                "\"confirmed_reward_per_person\": 100"
            );
        mockMvc.perform(post(
                "/classrooms/{classroomId}/ai/balancing-analysis-operations",
                classroomId
            )
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.overall_severity").value("BLOCK"))
            .andExpect(jsonPath("$.data.proposal_id").doesNotExist());
    }

    private UUID createProduct(String name) {
        return createProduct(name, 100);
    }

    private UUID createProduct(String name, long unitPrice) {
        UUID productId = UUID.randomUUID();
        productRepository.save(StoreProduct.draft(
            productId,
            classroomId,
            name,
            "Draft product",
            StoreProductKind.PHYSICAL,
            teacherId,
            NOW
        ));
        productVersionRepository.save(StoreProductVersion.publish(
            UUID.randomUUID(),
            classroomId,
            productId,
            1,
            StoreProductKind.PHYSICAL,
            unitPrice,
            0,
            false,
            Map.of(),
            NOW
        ));
        stockRepository.save(StoreStock.initialize(productId, classroomId, 10, NOW));
        return productId;
    }

    private UUID proposalId(MvcResult result) throws Exception {
        return UUID.fromString(objectMapper.readTree(
            result.getResponse().getContentAsString()
        ).path("data").path("proposal_id").asText());
    }

    private String applyWithoutEdits(String reason) {
        return """
            {
              "command": "APPLY",
              "edited_values": [],
              "reason": "%s",
              "expected_policy_version": 0
            }
            """.formatted(reason);
    }

    private MvcResult analyze(String body) throws Exception {
        return mockMvc.perform(post(
                "/classrooms/{classroomId}/ai/balancing-analysis-operations",
                classroomId
            )
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .header("X-Correlation-Id", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.proposal_id").isNotEmpty())
            .andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions apply(
        UUID proposalId,
        UUID requestId,
        String body
    ) throws Exception {
        return mockMvc.perform(post(
                "/classrooms/{classroomId}/ai/balancing-proposals/{proposalId}/commands",
                classroomId,
                proposalId
            )
                .principal(owner)
                .header("Idempotency-Key", requestId)
                .header("X-Correlation-Id", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String productAnalysisBody(UUID productId) {
        return """
            {
              "analysis_mode": "SETUP_PRECHECK",
              "trigger_context": {
                "context_type": "PRODUCT_DRAFT",
                "active_student_count": 24,
                "system_amount_cap": 1000,
                "product_draft": {
                  "target_id": "%s",
                  "kind": "NORMAL",
                  "stock": 10,
                  "expected_demand": 20,
                  "expected_purchase_frequency": 1.0,
                  "vat_bps": 0,
                  "free": false,
                  "confirmed_price": 100
                }
              }
            }
            """.formatted(productId);
    }

    private String quizAnalysisBody(UUID sessionId) {
        return """
            {
              "analysis_mode": "SETUP_PRECHECK",
              "trigger_context": {
                "context_type": "QUIZ_SESSION_DRAFT",
                "active_student_count": 24,
                "non_reward_issuance": 100000,
                "existing_reward_issuance": 0,
                "quiz_draft": {
                  "target_id": "%s",
                  "mode": "GAME",
                  "category": "ECONOMY",
                  "question_count": 5,
                  "expected_participants": 10,
                  "expected_runs": 1,
                  "confirmed_reward_per_person": 0
                }
              }
            }
            """.formatted(sessionId);
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
