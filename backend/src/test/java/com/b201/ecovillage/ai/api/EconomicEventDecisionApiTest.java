package com.b201.ecovillage.ai.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.ai.domain.EconomicEventDraft;
import com.b201.ecovillage.ai.repository.EconomicEventDraftRepository;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomTeacher;
import com.b201.ecovillage.identity.domain.ClassroomTeacherRole;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomTeacherRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

/**
 * API-AI-006 경제 이벤트 승인·거절 검증이다. 이 경로는 AI를 호출하지 않으므로
 * 공급자 대역이 필요하지 않고 실제 크레딧도 쓰지 않는다.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:economic-event-decision;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "ecovillage.ai-monitor.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class EconomicEventDecisionApiTest {

    private static final String OWNER_SUBJECT = "event-decision-owner";
    private static final String CO_TEACHER_SUBJECT = "event-decision-co-teacher";
    private static final Instant NOW = Instant.parse("2026-08-03T01:00:00Z");
    private static final String ECONOMIC_POLICY_AREA = "ECONOMIC";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomTeacherRepository teacherRepository;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private EconomicEventDraftRepository draftRepository;

    @Autowired
    private ClassroomPolicyVersionRepository policyRepository;

    private UUID classroomId;
    private Principal owner;
    private Principal coTeacher;

    @BeforeEach
    void setUp() {
        classroomId = UUID.randomUUID();
        owner = () -> OWNER_SUBJECT;
        coTeacher = () -> CO_TEACHER_SUBJECT;

        UUID ownerId = UUID.randomUUID();
        userRepository.save(UserAccount.createTeacher(ownerId, OWNER_SUBJECT, "이벤트 담임", NOW));
        UUID coTeacherId = UUID.randomUUID();
        userRepository.save(UserAccount.createTeacher(coTeacherId, CO_TEACHER_SUBJECT, "이벤트 부담임", NOW));

        classroomRepository.save(Classroom.create(
            classroomId,
            UUID.randomUUID(),
            UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(),
            "ECO",
            6,
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
            ownerId,
            ClassroomTeacherRole.OWNER,
            NOW
        ));
        teacherRepository.save(ClassroomTeacher.assign(
            UUID.randomUUID(),
            classroomId,
            coTeacherId,
            ClassroomTeacherRole.COT,
            NOW
        ));
        entityManager.flush();
    }

    @Test
    void appliesIncomeTaxRateAndPublishesApprovedTaxEvent() throws Exception {
        EconomicEventDraft draft = saveDraft("TAX", null);

        mockMvc.perform(decision(owner, draft.getId(), """
                {
                  "decision": "APPROVE",
                  "edits": { "income_tax_rate_bps": 1200 },
                  "reason": "물가 안정을 위해 소득세율을 올립니다.",
                  "expected_event_version": %d
                }
                """.formatted(draft.getStateVersion())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.event_id").value(draft.getId().toString()))
            .andExpect(jsonPath("$.data.status").value("APPROVED"))
            .andExpect(jsonPath("$.data.published_at").isNotEmpty());

        entityManager.flush();
        entityManager.clear();
        assertEquals(
            "APPLIED",
            draftRepository.findById(draft.getId()).orElseThrow().getStatus(),
            "정책 효과가 있는 승인은 APPLIED로 남는다."
        );
        ClassroomPolicyVersion policy = economicPolicy();
        assertEquals(1, policy.getVersion());
        assertEquals(1200, ((Number) policy.getPolicyValues().get("income_tax_rate_bps")).intValue());
    }

    @Test
    void keepsDomainUnchangedWhenTeacherRejects() throws Exception {
        EconomicEventDraft draft = saveDraft("TAX", null);

        mockMvc.perform(decision(owner, draft.getId(), """
                {
                  "decision": "REJECT",
                  "reason": "이번 주에는 세금 변화를 다루지 않습니다.",
                  "expected_event_version": %d
                }
                """.formatted(draft.getStateVersion())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
            .andExpect(jsonPath("$.data.published_at").doesNotExist());

        entityManager.flush();
        entityManager.clear();
        assertEquals("CANCELED", draftRepository.findById(draft.getId()).orElseThrow().getStatus());
        assertNull(draftRepository.findById(draft.getId()).orElseThrow().getRevealedAt());
        assertTrue(
            policyRepository
                .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(classroomId, ECONOMIC_POLICY_AREA)
                .isEmpty(),
            "거절은 어떤 정책 변경도 만들지 않는다."
        );
    }

    @Test
    void publishesPublicFundEventWithoutChangingPolicy() throws Exception {
        EconomicEventDraft draft = saveDraft("PUBLIC_FUND", null);

        mockMvc.perform(decision(owner, draft.getId(), """
                {
                  "decision": "APPROVE",
                  "reason": "공동기금 사용처를 학급 회의에서 정합니다.",
                  "expected_event_version": %d
                }
                """.formatted(draft.getStateVersion())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"))
            .andExpect(jsonPath("$.data.published_at").isNotEmpty());

        entityManager.flush();
        entityManager.clear();
        assertEquals("APPROVED", draftRepository.findById(draft.getId()).orElseThrow().getStatus());
        assertTrue(
            policyRepository
                .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(classroomId, ECONOMIC_POLICY_AREA)
                .isEmpty(),
            "공동기금 안내는 실제 지출을 만들지 않으므로 정책도 바꾸지 않는다."
        );
    }

    @Test
    void rejectsEditsOutsideTheTemplateEffectScope() throws Exception {
        EconomicEventDraft draft = saveDraft("TAX", null);

        mockMvc.perform(decision(owner, draft.getId(), """
                {
                  "decision": "APPROVE",
                  "edits": { "deposit_rate_bps": 400 },
                  "reason": "세금 이벤트로 예금 금리를 바꿔봅니다.",
                  "expected_event_version": %d
                }
                """.formatted(draft.getStateVersion())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN_EVENT_EFFECT"));

        entityManager.flush();
        entityManager.clear();
        assertEquals("DRAFT", draftRepository.findById(draft.getId()).orElseThrow().getStatus());
    }

    @Test
    void rejectsEditsForTemplateWithoutPolicyEffect() throws Exception {
        EconomicEventDraft draft = saveDraft("PUBLIC_FUND", null);

        mockMvc.perform(decision(owner, draft.getId(), """
                {
                  "decision": "APPROVE",
                  "edits": { "income_tax_rate_bps": 900 },
                  "reason": "공동기금 안내로 세율을 바꿔봅니다.",
                  "expected_event_version": %d
                }
                """.formatted(draft.getStateVersion())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN_EVENT_EFFECT"));
    }

    @Test
    void requiresEditsWhenTemplateChangesPolicy() throws Exception {
        EconomicEventDraft draft = saveDraft("INTEREST_RATE", "DEPOSIT");

        mockMvc.perform(decision(owner, draft.getId(), """
                {
                  "decision": "APPROVE",
                  "reason": "금리를 올린다고만 하고 값은 안 정합니다.",
                  "expected_event_version": %d
                }
                """.formatted(draft.getStateVersion())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void appliesLoanRateOnlyWhenTheDraftScopeIsLoan() throws Exception {
        EconomicEventDraft draft = saveDraft("INTEREST_RATE", "LOAN");

        mockMvc.perform(decision(owner, draft.getId(), """
                {
                  "decision": "APPROVE",
                  "edits": { "loan_rate_bps": 700 },
                  "reason": "대출 금리를 올려 과열을 식힙니다.",
                  "expected_event_version": %d
                }
                """.formatted(draft.getStateVersion())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"));

        entityManager.flush();
        entityManager.clear();
        assertEquals(700, ((Number) economicPolicy().getPolicyValues().get("loan_rate_bps")).intValue());
    }

    @Test
    void replaysTheSameRequestWithoutApplyingTwice() throws Exception {
        EconomicEventDraft draft = saveDraft("TAX", null);
        UUID requestId = UUID.randomUUID();
        String body = """
            {
              "decision": "APPROVE",
              "edits": { "income_tax_rate_bps": 1100 },
              "reason": "같은 요청을 두 번 보냅니다.",
              "expected_event_version": %d
            }
            """.formatted(draft.getStateVersion());

        mockMvc.perform(decision(owner, draft.getId(), body, requestId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"));
        mockMvc.perform(decision(owner, draft.getId(), body, requestId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"));

        entityManager.flush();
        entityManager.clear();
        List<ClassroomPolicyVersion> versions = policyRepository
            .findAllByClassroomIdAndPolicyAreaOrderByVersion(classroomId, ECONOMIC_POLICY_AREA);
        assertEquals(1, versions.size(), "재시도가 정책 버전을 두 번 만들지 않는다.");
    }

    @Test
    void rejectsDecisionOnAnAlreadyDecidedDraft() throws Exception {
        EconomicEventDraft draft = saveDraft("PUBLIC_FUND", null);
        mockMvc.perform(decision(owner, draft.getId(), """
                {
                  "decision": "APPROVE",
                  "reason": "먼저 승인합니다.",
                  "expected_event_version": %d
                }
                """.formatted(draft.getStateVersion())))
            .andExpect(status().isOk());
        entityManager.flush();

        mockMvc.perform(decision(owner, draft.getId(), """
                {
                  "decision": "REJECT",
                  "reason": "승인한 뒤에 다시 거절해 봅니다.",
                  "expected_event_version": 0
                }
                """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("STATE_VERSION_CONFLICT"));
    }

    @Test
    void rejectsStaleExpectedEventVersion() throws Exception {
        EconomicEventDraft draft = saveDraft("TAX", null);

        mockMvc.perform(decision(owner, draft.getId(), """
                {
                  "decision": "APPROVE",
                  "edits": { "income_tax_rate_bps": 1000 },
                  "reason": "오래된 버전으로 승인해 봅니다.",
                  "expected_event_version": %d
                }
                """.formatted(draft.getStateVersion() + 5)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("STATE_VERSION_CONFLICT"));
    }

    @Test
    void blocksCoTeacherFromApplyingAnEconomicPolicyEffect() throws Exception {
        EconomicEventDraft draft = saveDraft("TAX", null);

        // PM-AI-002는 부담임의 승인·거절을 허용하지만 경제 정책 변경은 PM-POL-001로 담임 전용이다.
        mockMvc.perform(decision(coTeacher, draft.getId(), """
                {
                  "decision": "APPROVE",
                  "edits": { "income_tax_rate_bps": 1000 },
                  "reason": "부담임이 세율을 바꿔봅니다.",
                  "expected_event_version": %d
                }
                """.formatted(draft.getStateVersion())))
            .andExpect(status().isForbidden());

        entityManager.flush();
        entityManager.clear();
        assertEquals("DRAFT", draftRepository.findById(draft.getId()).orElseThrow().getStatus());
    }

    @Test
    void letsCoTeacherRejectADraft() throws Exception {
        EconomicEventDraft draft = saveDraft("TAX", null);

        mockMvc.perform(decision(coTeacher, draft.getId(), """
                {
                  "decision": "REJECT",
                  "reason": "부담임이 이벤트를 되돌립니다.",
                  "expected_event_version": %d
                }
                """.formatted(draft.getStateVersion())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void rejectsInvestmentNewsUntilTheInvestmentDomainExists() throws Exception {
        EconomicEventDraft draft = saveDraft("INVESTMENT_NEWS", null);

        // S15P11B201-289가 투자 도메인을 만들기 전에는 가격 단계에 연결할 수 없다.
        mockMvc.perform(decision(owner, draft.getId(), """
                {
                  "decision": "APPROVE",
                  "reason": "투자 뉴스를 게시해 봅니다.",
                  "expected_event_version": %d
                }
                """.formatted(draft.getStateVersion())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("INVALID_INVESTMENT_STEP_BINDING"));
    }

    private ClassroomPolicyVersion economicPolicy() {
        return policyRepository
            .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(classroomId, ECONOMIC_POLICY_AREA)
            .orElseThrow();
    }

    private EconomicEventDraft saveDraft(String templateType, String interestRateScope) {
        java.util.Map<String, Object> effect = new java.util.LinkedHashMap<>();
        effect.put("template_type", templateType);
        effect.put("source_type", "TEACHER_REQUEST");
        effect.put("interest_rate_scope", interestRateScope);
        effect.put("effect_summary", List.of("무엇이 바뀌는지 문장으로만 고정합니다."));
        effect.put("prohibited_effects", List.of("개인 잔액과 계좌의 직접 변경"));
        EconomicEventDraft draft = draftRepository.save(EconomicEventDraft.draft(
            UUID.randomUUID(),
            classroomId,
            null,
            "TEACHER_REQUEST",
            null,
            "이벤트 제목",
            "AI가 만든 이벤트 이야기입니다.",
            effect,
            NOW
        ));
        entityManager.flush();
        return draft;
    }

    private MockHttpServletRequestBuilder decision(Principal actor, UUID eventId, String body) {
        return decision(actor, eventId, body, UUID.randomUUID());
    }

    private MockHttpServletRequestBuilder decision(
        Principal actor,
        UUID eventId,
        String body,
        UUID requestId
    ) {
        return post(
            "/classrooms/{classroomId}/ai/economic-events/{eventId}/decisions",
            classroomId,
            eventId
        )
            .principal(actor)
            .header("Idempotency-Key", requestId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    }
}
