package com.b201.ecovillage.ai.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.b201.ecovillage.ai.application.ClassroomAiSettings;
import com.b201.ecovillage.ai.application.TeacherChatFallbackFactory;
import com.b201.ecovillage.ai.contract.TeacherChatContract;
import com.b201.ecovillage.ai.domain.AiRun;
import com.b201.ecovillage.ai.infrastructure.TeacherChatAiClient;
import com.b201.ecovillage.ai.repository.AiRunRepository;
import com.b201.ecovillage.audit.repository.AuditEventRepository;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomTeacher;
import com.b201.ecovillage.identity.domain.ClassroomTeacherRole;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomTeacherRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ai-settings;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "ecovillage.ai-monitor.enabled=false"
})
@AutoConfigureMockMvc
@Import(AiSettingsApiTest.FakeChatConfiguration.class)
@Transactional
class AiSettingsApiTest {

    private static final String OWNER_SUBJECT = "ai-settings-owner";
    private static final String CO_TEACHER_SUBJECT = "ai-settings-co-teacher";
    private static final Instant NOW = Instant.parse("2026-08-01T01:00:00Z");
    private static final Instant PERIOD_FROM = Instant.parse("2026-07-25T00:00:00Z");
    private static final Instant PERIOD_TO = Instant.parse("2026-08-01T00:00:00Z");

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
    private ClassroomPolicyVersionRepository policyRepository;

    @Autowired
    private AuditEventRepository auditRepository;

    @Autowired
    private AiRunRepository runRepository;

    @Autowired
    private ClassroomAiSettings aiSettings;

    @Autowired
    private CountingChatClient chatClient;

    private UUID classroomId;
    private UUID teacherId;
    private Principal owner;
    private Principal coTeacher;

    @BeforeEach
    void setUp() {
        chatClient.calls = 0;
        classroomId = UUID.randomUUID();
        teacherId = UUID.randomUUID();
        owner = () -> OWNER_SUBJECT;
        coTeacher = () -> CO_TEACHER_SUBJECT;
        userRepository.save(UserAccount.createTeacher(teacherId, OWNER_SUBJECT, "담임", NOW));
        UUID coTeacherId = UUID.randomUUID();
        userRepository.save(UserAccount.createTeacher(
            coTeacherId,
            CO_TEACHER_SUBJECT,
            "부담임",
            NOW
        ));
        classroomRepository.save(Classroom.create(
            classroomId,
            UUID.randomUUID(),
            UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(),
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
    void ownerDisablesClassroomAiAndRecordsPolicyVersionWithAudit() throws Exception {
        assertTrue(aiSettings.enabled(classroomId), "설정 전에는 AI를 사용하는 상태여야 합니다.");

        mockMvc.perform(disableRequest(owner, "수업 중 AI 사용을 잠시 멈춥니다.", null))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ai_resource_id").isNotEmpty())
            .andExpect(jsonPath("$.data.status").value("DISABLED"))
            .andExpect(jsonPath("$.data.state_version").value(1));

        entityManager.flush();
        entityManager.clear();
        assertFalse(aiSettings.enabled(classroomId), "설정 후에는 AI가 꺼져야 합니다.");
        assertEquals(1, policyRepository
            .findAllByClassroomIdAndSupersededAtIsNullOrderByPolicyArea(classroomId)
            .size());
        assertEquals(1, auditRepository.count());
    }

    @Test
    void secondDisableSupersedesPreviousVersion() throws Exception {
        mockMvc.perform(disableRequest(owner, "1차 중지", null))
            .andExpect(status().isOk());
        entityManager.flush();

        mockMvc.perform(disableRequest(owner, "2차 중지", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.state_version").value(2));

        entityManager.flush();
        entityManager.clear();
        assertEquals(1, policyRepository
            .findAllByClassroomIdAndSupersededAtIsNullOrderByPolicyArea(classroomId)
            .size(), "활성 정책 버전은 항상 하나여야 합니다.");
    }

    @Test
    void rejectsStaleExpectedStateVersion() throws Exception {
        mockMvc.perform(disableRequest(owner, "1차 중지", null))
            .andExpect(status().isOk());
        entityManager.flush();

        mockMvc.perform(disableRequest(owner, "잘못된 버전", 0L))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("POLICY_VERSION_CONFLICT"));
    }

    @Test
    void rejectsCoTeacherBecauseSettingIsOwnerOnly() throws Exception {
        mockMvc.perform(disableRequest(coTeacher, "부담임이 끄려고 시도", null))
            .andExpect(status().isForbidden());

        assertTrue(aiSettings.enabled(classroomId));
    }

    @Test
    void rejectsEnableBecauseContractAllowsDisableOnly() throws Exception {
        mockMvc.perform(put("/classrooms/{classroomId}/ai-settings", classroomId)
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "enabled": true, "reason": "다시 켜기" }
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    /**
     * APPLYING은 적용을 비동기로 바꿀 때 쓰는 상태이며 현재 동기 적용 흐름에서는 만들어지지 않는다.
     * 도메인에 테스트 전용 전이를 추가하지 않고 상태만 직접 만들어 가드를 검증한다.
     */
    @Test
    void rejectsWhileAiProposalIsBeingApplied() throws Exception {
        UUID runId = UUID.randomUUID();
        runRepository.save(AiRun.generating(
            runId,
            classroomId,
            null,
            teacherId,
            "SETUP_PRECHECK",
            "TEACHER_REQUEST",
            TeacherChatContract.PROMPT_VERSION,
            Map.of(),
            NOW
        ));
        entityManager.flush();
        entityManager.createQuery("update AiRun r set r.status = 'APPLYING' where r.id = :id")
            .setParameter("id", runId)
            .executeUpdate();
        entityManager.clear();

        mockMvc.perform(disableRequest(owner, "적용 중 중지 시도", null))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("AI_APPLICATION_IN_PROGRESS"));
    }

    @Test
    void disabledClassroomAnswersChatWithoutCallingTheAiService() throws Exception {
        mockMvc.perform(disableRequest(owner, "AI 중지", null))
            .andExpect(status().isOk());
        entityManager.flush();

        mockMvc.perform(post("/classrooms/{classroomId}/ai/chat-requests", classroomId)
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "question": "AI를 껐는데 답변이 되나요?",
                      "period": { "from": "%s", "to": "%s" },
                      "metric_scopes": ["ISSUANCE_AMOUNT"]
                    }
                    """.formatted(PERIOD_FROM, PERIOD_TO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.answer").value(
                "이 학급은 AI 기능을 꺼 두었습니다. 학급 지표는 운영 현황 화면에서 확인해 주세요."
            ))
            .andExpect(jsonPath("$.data.evidence[0].fact_id").isNotEmpty());

        assertEquals(0, chatClient.calls, "AI를 끈 학급에서는 FastAPI를 호출하지 않아야 합니다.");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder disableRequest(
        Principal principal,
        String reason,
        Long expectedStateVersion
    ) {
        String body = expectedStateVersion == null
            ? """
                { "enabled": false, "reason": "%s" }
                """.formatted(reason)
            : """
                { "enabled": false, "reason": "%s", "expected_state_version": %d }
                """.formatted(reason, expectedStateVersion);
        return put("/classrooms/{classroomId}/ai-settings", classroomId)
            .principal(principal)
            .header("Idempotency-Key", UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    }

    static class CountingChatClient implements TeacherChatAiClient {

        private final TeacherChatFallbackFactory fallbackFactory;
        private int calls;

        CountingChatClient(TeacherChatFallbackFactory fallbackFactory) {
            this.fallbackFactory = fallbackFactory;
        }

        @Override
        public TeacherChatContract.Response answer(TeacherChatContract.Request request) {
            calls++;
            TeacherChatContract.Response template = fallbackFactory.create(
                request,
                "FAKE_PROVIDER",
                "정상 답변입니다."
            );
            return new TeacherChatContract.Response(
                template.aiRunId(),
                template.schemaVersion(),
                template.promptVersion(),
                "FAKE",
                "fake-chat-model",
                template.safetyStatus(),
                List.of(),
                template.usage(),
                template.output()
            );
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeChatConfiguration {

        @Bean
        @Primary
        Clock fixedAiSettingsClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        CountingChatClient countingChatClient(TeacherChatFallbackFactory fallbackFactory) {
            return new CountingChatClient(fallbackFactory);
        }
    }
}
