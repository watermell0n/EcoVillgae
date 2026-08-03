package com.b201.ecovillage.ai.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import com.b201.ecovillage.ai.application.TeacherChatFallbackFactory;
import com.b201.ecovillage.ai.contract.TeacherChatContract;
import com.b201.ecovillage.ai.domain.AiRun;
import com.b201.ecovillage.ai.infrastructure.AiServiceCallException;
import com.b201.ecovillage.ai.infrastructure.TeacherChatAiClient;
import com.b201.ecovillage.ai.repository.AiArtifactRepository;
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

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:teacher-chat;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "ecovillage.ai-monitor.enabled=false"
})
@AutoConfigureMockMvc
@Import(TeacherChatApiTest.FakeChatConfiguration.class)
@Transactional
class TeacherChatApiTest {

    private static final String OWNER_SUBJECT = "teacher-chat-owner";
    private static final String OUTSIDER_SUBJECT = "teacher-chat-outsider";
    private static final Instant NOW = Instant.parse("2026-08-01T01:00:00Z");
    private static final Instant PERIOD_FROM = Instant.parse("2026-07-25T00:00:00Z");
    private static final Instant PERIOD_TO = Instant.parse("2026-08-01T00:00:00Z");
    private static final String STUDENT_NICKNAME = "김하늘";

    @Autowired
    private MockMvc mockMvc;

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
    private AiRunRepository runRepository;

    @Autowired
    private AiArtifactRepository artifactRepository;

    @Autowired
    private RecordingTeacherChatClient aiClient;

    private UUID classroomId;
    private UUID teacherId;
    private Principal owner;
    private Principal outsider;

    @BeforeEach
    void setUp() {
        aiClient.reset();
        classroomId = UUID.randomUUID();
        teacherId = UUID.randomUUID();
        owner = () -> OWNER_SUBJECT;
        outsider = () -> OUTSIDER_SUBJECT;
        userRepository.save(UserAccount.createTeacher(teacherId, OWNER_SUBJECT, "챗봇 담임", NOW));
        userRepository.save(UserAccount.createTeacher(
            UUID.randomUUID(),
            OUTSIDER_SUBJECT,
            "다른 학급 교사",
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
        studentRepository.save(ClassroomStudent.enroll(
            UUID.randomUUID(),
            classroomId,
            1,
            STUDENT_NICKNAME,
            PERIOD_FROM.minusSeconds(86_400)
        ));
        entityManager.flush();
    }

    @Test
    void answersWithClassroomFactsAndRecordsRun() throws Exception {
        mockMvc.perform(chatRequest(owner, "우리 반 화폐가 잘 돌고 있나요?"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ai_run_id").isNotEmpty())
            .andExpect(jsonPath("$.data.answer").value("학급 집계를 근거로 설명합니다."))
            .andExpect(jsonPath("$.data.period.from").exists())
            .andExpect(jsonPath("$.data.period.to").exists())
            .andExpect(jsonPath("$.data.data_as_of").exists())
            .andExpect(jsonPath("$.data.evidence[0].fact_id").value("CLASSROOM_CURRENCY"))
            .andExpect(jsonPath("$.data.recommendations[0].side_effects[0]").isNotEmpty());

        assertEquals(1, aiClient.calls);
        AiRun run = runRepository.findAll().stream()
            .filter(item -> "TEACHER_CHAT".equals(item.getRunType()))
            .findFirst()
            .orElseThrow();
        assertEquals("COMPLETED", run.getStatus());
        assertEquals(1, artifactRepository.count());
    }

    @Test
    void fallsBackToLimitedAnswerWhenFastApiFails() throws Exception {
        aiClient.fail = true;

        mockMvc.perform(chatRequest(owner, "지금 물가가 괜찮은가요?"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.answer").value(
                "AI 설명 연결에 실패해 답변을 만들지 못했습니다. 학급 지표는 운영 현황 화면에서 확인해 주세요."
            ))
            .andExpect(jsonPath("$.data.evidence[0].fact_id").value("CLASSROOM_CURRENCY"))
            .andExpect(jsonPath("$.data.recommendations").isEmpty());

        AiRun run = runRepository.findAll().stream()
            .filter(item -> "TEACHER_CHAT".equals(item.getRunType()))
            .findFirst()
            .orElseThrow();
        assertEquals("COMPLETED", run.getStatus());
        assertEquals("SPRING_FALLBACK", run.getModelProvider());
    }

    @Test
    void rejectsTeacherOfAnotherClassroom() throws Exception {
        mockMvc.perform(chatRequest(outsider, "다른 학급 정보를 알려주세요."))
            .andExpect(status().isForbidden());

        assertEquals(0, aiClient.calls);
    }

    @Test
    void rejectsWhenDailyChatLimitIsReached() throws Exception {
        for (int index = 0; index < 30; index++) {
            AiRun used = AiRun.generating(
                UUID.randomUUID(),
                classroomId,
                null,
                teacherId,
                "TEACHER_CHAT",
                "TEACHER_REQUEST",
                TeacherChatContract.PROMPT_VERSION,
                Map.of(),
                NOW
            );
            used.completeWithModel("FAKE", "fake-chat-model", Map.of(), NOW);
            runRepository.save(used);
        }
        entityManager.flush();

        mockMvc.perform(chatRequest(owner, "오늘 질문을 더 할 수 있나요?"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error.code").value("AI_CHAT_DAILY_LIMIT_EXCEEDED"));

        assertEquals(0, aiClient.calls);
    }

    @Test
    void doesNotSendStudentIdentifiersToTheAiService() throws Exception {
        mockMvc.perform(chatRequest(owner, "학생들이 돈을 잘 쓰고 있나요?"))
            .andExpect(status().isOk());

        TeacherChatContract.Request captured = aiClient.lastRequest;
        assertNotNull(captured);
        String payload = captured.payload().toString();
        assertFalse(payload.contains(STUDENT_NICKNAME), "학생 닉네임이 AI 입력에 포함되면 안 됩니다.");
        assertTrue(
            captured.payload().facts().stream()
                .anyMatch(fact -> "CLASSROOM_ACTIVE_STUDENTS".equals(fact.factId())),
            "집계된 학생 수는 포함되어야 합니다."
        );
        assertEquals(TeacherChatContract.PROMPT_VERSION, captured.promptVersion());
        assertEquals("1.2", captured.schemaVersion());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder chatRequest(
        Principal principal,
        String question
    ) {
        return post("/classrooms/{classroomId}/ai/chat-requests", classroomId)
            .principal(principal)
            .header("Idempotency-Key", UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "question": "%s",
                  "period": { "from": "%s", "to": "%s" },
                  "metric_scopes": ["ISSUANCE_AMOUNT", "CONSUMPTION_RATE_BPS"]
                }
                """.formatted(question, PERIOD_FROM, PERIOD_TO));
    }

    private static String randomClassroomCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    static class RecordingTeacherChatClient implements TeacherChatAiClient {

        private final TeacherChatFallbackFactory fallbackFactory;
        private int calls;
        private boolean fail;
        private TeacherChatContract.Request lastRequest;

        RecordingTeacherChatClient(TeacherChatFallbackFactory fallbackFactory) {
            this.fallbackFactory = fallbackFactory;
        }

        void reset() {
            calls = 0;
            fail = false;
            lastRequest = null;
        }

        @Override
        public TeacherChatContract.Response answer(TeacherChatContract.Request request) {
            calls++;
            lastRequest = request;
            if (fail) {
                throw new AiServiceCallException("AI_TIMEOUT", "simulated timeout");
            }
            TeacherChatContract.Fact first = request.payload().facts().get(0);
            TeacherChatContract.Response template = fallbackFactory.create(
                request,
                "FAKE_PROVIDER",
                "학급 집계를 근거로 설명합니다."
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
                new TeacherChatContract.Output(
                    TeacherChatContract.ResponseMode.ANSWER,
                    "학급 집계를 근거로 설명합니다.",
                    List.of(first.factId()),
                    List.of(new TeacherChatContract.Cause(
                        "집계된 사실에서 원인을 확인했습니다.",
                        List.of(first.factId())
                    )),
                    List.of(new TeacherChatContract.Recommendation(
                        "상품 가격을 점검해 주세요.",
                        List.of(first.factId()),
                        List.of("학생 구매력이 달라질 수 있습니다.")
                    )),
                    List.of(),
                    request.payload().period(),
                    request.payload().period().dataAsOf()
                )
            );
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeChatConfiguration {

        @Bean
        @Primary
        Clock fixedChatClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        RecordingTeacherChatClient recordingTeacherChatClient(
            TeacherChatFallbackFactory fallbackFactory
        ) {
            return new RecordingTeacherChatClient(fallbackFactory);
        }
    }
}
