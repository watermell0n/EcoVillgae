package com.b201.ecovillage.ai.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.LedgerActorType;
import com.b201.ecovillage.ledger.domain.TransactionGroup;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ledger.repository.TransactionBusinessRefRepository;
import com.b201.ecovillage.ledger.repository.TransactionGroupRepository;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomTeacher;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.domain.ClassroomTeacherRole;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomTeacherRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.realtime.domain.OutboxEvent;
import com.b201.ecovillage.realtime.repository.OutboxEventRepository;

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
    private static final String MONEY_ISSUANCE_KEY = "MONEY_ISSUANCE";
    private static final String CLASSROOM_FUND_KEY = "CLASSROOM_FUND";

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

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private ClassroomStudentRepository studentRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private TransactionGroupRepository transactionGroupRepository;

    @Autowired
    private TransactionBusinessRefRepository businessRefRepository;

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
    void grantsPublicFundFromTheIssuanceAccount() throws Exception {
        EconomicEventDraft draft = saveDraft("PUBLIC_FUND", null);

        mockMvc.perform(decision(owner, draft.getId(), """
                {
                  "decision": "APPROVE",
                  "edits": { "public_fund_grant_amount": 30000 },
                  "reason": "학급 공동기금을 채웁니다.",
                  "expected_event_version": %d
                }
                """.formatted(draft.getStateVersion())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"))
            .andExpect(jsonPath("$.data.published_at").isNotEmpty());

        entityManager.flush();
        entityManager.clear();
        TransactionGroup group = payoutGroup(draft.getId());
        assertEquals("ECONOMIC_EVENT_PUBLIC_FUND_GRANT", group.getTransactionType());
        assertEquals(LedgerActorType.SYSTEM, group.getActorType(), "집행 주체는 시스템이다.");
        // 공동기금은 공공 지출·투표에서만 차감한다. 이벤트는 발행 계좌에서 교부만 한다.
        assertEquals(30000L, balanceOf(LedgerAccountType.CLASSROOM_FUND, CLASSROOM_FUND_KEY));
        assertEquals(-30000L, balanceOf(LedgerAccountType.MONEY_ISSUANCE, MONEY_ISSUANCE_KEY));
        assertTrue(
            policyRepository
                .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(classroomId, ECONOMIC_POLICY_AREA)
                .isEmpty(),
            "화폐가 움직이는 효과는 경제 정책을 바꾸지 않는다."
        );
    }

    @Test
    void issuesRewardToEveryActiveStudent() throws Exception {
        enrollStudents(3);
        EconomicEventDraft draft = saveDraft("REWARD", null);

        mockMvc.perform(decision(owner, draft.getId(), """
                {
                  "decision": "APPROVE",
                  "edits": { "reward_per_student": 500 },
                  "reason": "활동을 마친 학생 전원에게 보상을 지급합니다.",
                  "expected_event_version": %d
                }
                """.formatted(draft.getStateVersion())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"));

        entityManager.flush();
        entityManager.clear();
        TransactionGroup group = payoutGroup(draft.getId());
        assertEquals("ECONOMIC_EVENT_REWARD_ISSUANCE", group.getTransactionType());
        assertEquals(LedgerActorType.SYSTEM, group.getActorType());
        // 공동기금이 아니라 발행 계좌에서 나간다. 기금 잔액 부족으로 수업이 막히지 않게 한다.
        assertEquals(-1500L, balanceOf(LedgerAccountType.MONEY_ISSUANCE, MONEY_ISSUANCE_KEY));
        assertTrue(
            ledgerAccountRepository
                .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                    classroomId,
                    CLASSROOM_FUND_KEY,
                    LedgerAccountType.CLASSROOM_FUND,
                    LedgerAccountStatus.ACTIVE
                )
                .isEmpty(),
            "보상 지급은 공동기금 계좌를 건드리지 않는다."
        );
    }

    @Test
    void doesNotPayTwiceForTheSameDraft() throws Exception {
        enrollStudents(2);
        EconomicEventDraft draft = saveDraft("REWARD", null);
        String body = """
            {
              "decision": "APPROVE",
              "edits": { "reward_per_student": 700 },
              "reason": "같은 초안을 두 번 승인해 봅니다.",
              "expected_event_version": %d
            }
            """.formatted(draft.getStateVersion());

        mockMvc.perform(decision(owner, draft.getId(), body)).andExpect(status().isOk());
        entityManager.flush();
        // 두 번째는 초안이 이미 결정되어 409로 막히지만, business ref로도 이중 지급을 막는다.
        mockMvc.perform(decision(owner, draft.getId(), body)).andExpect(status().isConflict());

        entityManager.flush();
        entityManager.clear();
        assertEquals(1, businessRefRepository.count(), "지급 거래는 한 번만 생긴다.");
        assertEquals(-1400L, balanceOf(LedgerAccountType.MONEY_ISSUANCE, MONEY_ISSUANCE_KEY));
    }

    @Test
    void keepsDraftUnderReviewWhenApplyingFails() throws Exception {
        // 활성 학생이 없으면 보상 지급이 실패한다. 초안은 DRAFT로 남아 재시도할 수 있어야 한다.
        EconomicEventDraft draft = saveDraft("REWARD", null);

        mockMvc.perform(decision(owner, draft.getId(), """
                {
                  "decision": "APPROVE",
                  "edits": { "reward_per_student": 500 },
                  "reason": "학생이 없는 학급에서 보상을 지급해 봅니다.",
                  "expected_event_version": %d
                }
                """.formatted(draft.getStateVersion())))
            .andExpect(status().is4xxClientError());

        entityManager.clear();
        EconomicEventDraft reloaded = draftRepository.findById(draft.getId()).orElseThrow();
        assertEquals("DRAFT", reloaded.getStatus(), "실패해도 FAILED로 고정하지 않는다.");
        assertNull(reloaded.getRevealedAt());
        assertEquals(0, businessRefRepository.count());
        assertTrue(
            outboxRepository.findAll().stream()
                .noneMatch(event -> "economic_event.published".equals(event.getEventType())),
            "실패한 승인은 게시 이벤트를 남기지 않는다."
        );
    }

    @Test
    void rejectsRewardAboveThePerStudentLimit() throws Exception {
        enrollStudents(1);
        EconomicEventDraft draft = saveDraft("REWARD", null);

        mockMvc.perform(decision(owner, draft.getId(), """
                {
                  "decision": "APPROVE",
                  "edits": { "reward_per_student": 100001 },
                  "reason": "한도를 넘겨 지급해 봅니다.",
                  "expected_event_version": %d
                }
                """.formatted(draft.getStateVersion())))
            .andExpect(status().is4xxClientError());

        entityManager.clear();
        assertEquals("DRAFT", draftRepository.findById(draft.getId()).orElseThrow().getStatus());
        assertEquals(0, businessRefRepository.count());
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
    void rejectsPolicyEditsOnALedgerTemplate() throws Exception {
        EconomicEventDraft draft = saveDraft("PUBLIC_FUND", null);

        mockMvc.perform(decision(owner, draft.getId(), """
                {
                  "decision": "APPROVE",
                  "edits": { "income_tax_rate_bps": 900 },
                  "reason": "공동기금 교부로 세율을 바꿔봅니다.",
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
                  "edits": { "public_fund_grant_amount": 10000 },
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

    @Test
    void publishesRealtimeEventOnApprovalOnly() throws Exception {
        EconomicEventDraft approved = saveDraft("PUBLIC_FUND", null);
        EconomicEventDraft rejected = saveDraft("PUBLIC_FUND", null);

        mockMvc.perform(decision(owner, approved.getId(), """
                {
                  "decision": "APPROVE",
                  "edits": { "public_fund_grant_amount": 12000 },
                  "reason": "학생에게 게시합니다.",
                  "expected_event_version": %d
                }
                """.formatted(approved.getStateVersion())))
            .andExpect(status().isOk());
        mockMvc.perform(decision(owner, rejected.getId(), """
                {
                  "decision": "REJECT",
                  "reason": "이건 게시하지 않습니다.",
                  "expected_event_version": %d
                }
                """.formatted(rejected.getStateVersion())))
            .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        List<OutboxEvent> events = outboxRepository.findAll().stream()
            .filter(event -> "economic_event.published".equals(event.getEventType()))
            .toList();
        assertEquals(1, events.size(), "승인만 EVT-ACT-007을 남긴다.");
        OutboxEvent event = events.get(0);
        assertEquals(approved.getId(), event.getAggregateId());
        assertEquals("PUBLIC_FUND", event.getPayload().get("template_type"));
        assertEquals(approved.getId().toString(), event.getPayload().get("event_id"));
        assertNotNull(event.getPayload().get("effective_from"));
        assertNull(event.getPublishedAt(), "소켓 전달은 아웃박스를 비우는 쪽이 담당한다.");
    }

    @Test
    void keepsPrivateFieldsOutOfThePublishedPayload() throws Exception {
        EconomicEventDraft draft = saveDraft("PUBLIC_FUND", null);

        mockMvc.perform(decision(owner, draft.getId(), """
                {
                  "decision": "APPROVE",
                  "edits": { "public_fund_grant_amount": 8000 },
                  "reason": "공개 안전성을 확인합니다.",
                  "expected_event_version": %d
                }
                """.formatted(draft.getStateVersion())))
            .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        OutboxEvent event = outboxRepository.findAll().stream()
            .filter(item -> "economic_event.published".equals(item.getEventType()))
            .findFirst()
            .orElseThrow();
        // WS-ECO-001은 학생도 받는 채널이다. 개인 식별자와 잔액이 들어가면 발행 자체가 실패해야 한다.
        assertEquals(
            java.util.Set.of("event_id", "template_type", "title", "description", "effective_from"),
            event.getPayload().keySet(),
            "EVT-ACT-007 명세의 다섯 필드만 내보낸다."
        );
    }

    /** 초안 id를 business reference로 따라가 지급 거래를 찾는다. */
    private TransactionGroup payoutGroup(UUID draftId) {
        UUID groupId = businessRefRepository
            .findTopByClassroomIdAndRefTypeAndRefIdOrderByCreatedAtDesc(
                classroomId,
                "ECONOMIC_EVENT_DRAFT",
                draftId
            )
            .orElseThrow()
            .getTransactionGroupId();
        return transactionGroupRepository.findById(groupId).orElseThrow();
    }

    private void enrollStudents(int count) {
        for (int rosterNumber = 1; rosterNumber <= count; rosterNumber++) {
            studentRepository.save(ClassroomStudent.enroll(
                UUID.randomUUID(),
                classroomId,
                rosterNumber,
                "학생" + rosterNumber,
                NOW
            ));
        }
        entityManager.flush();
    }

    private long balanceOf(LedgerAccountType accountType, String ownerKey) {
        return ledgerAccountRepository
            .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                classroomId,
                ownerKey,
                accountType,
                LedgerAccountStatus.ACTIVE
            )
            .orElseThrow()
            .getCurrentBalance();
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
