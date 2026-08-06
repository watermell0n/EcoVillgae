package com.b201.ecovillage.quiz.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import com.b201.ecovillage.ai.contract.QuizDraftContract;
import com.b201.ecovillage.ai.infrastructure.QuizDraftAiClient;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomTeacher;
import com.b201.ecovillage.identity.domain.ClassroomTeacherRole;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomTeacherRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

/**
 * 교사가 실제로 겪는 경로를 그대로 재현한다.
 * AI 초안 생성 -> 문제은행 조회 -> 검토 후 승인 -> 문제은행에 게시.
 *
 * 요청마다 트랜잭션이 분리되도록 NOT_SUPPORTED를 쓴다. 테스트 전체를 하나의
 * 트랜잭션으로 묶으면 영속성 컨텍스트가 재사용되어 state_version 문제가 가려진다.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ai-draft-approval;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.task.scheduling.enabled=false",
    "ecovillage.ai-monitor.enabled=false"
})
@AutoConfigureMockMvc
@Import(AiDraftApprovalFlowApiTest.FakeQuizConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AiDraftApprovalFlowApiTest {

    private static final Instant NOW = Instant.parse("2026-08-01T01:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ClassroomRepository classroomRepository;
    @Autowired private ClassroomTeacherRepository teacherRepository;
    @Autowired private UserAccountRepository userRepository;

    private UUID classroomId;
    private Principal owner;

    @BeforeEach
    void setUp() {
        classroomId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        owner = () -> "ai-draft-owner-" + classroomId;

        userRepository.save(UserAccount.createTeacher(teacherId, owner.getName(), "퀴즈 담임", NOW));
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
    }

    @Test
    void approvesAndPublishesAnAiDraftExactlyAsTheTeacherScreenDoes() throws Exception {
        generateDrafts(1);

        JsonNode question = questions().path(0);
        assertEquals("REVIEW_REQUIRED", question.path("status").asText());
        assertEquals("AI", question.path("source").asText());
        String questionId = question.path("resource_id").asText();
        long stateVersion = question.path("state_version").asLong();
        assertNotEquals("", question.path("question_version_id").asText());

        // 교사 화면이 조회 응답의 state_version을 그대로 되돌려 보낸다.
        JsonNode approved = statusCommand(questionId, "APPROVE", stateVersion);
        assertEquals("APPROVED", approved.path("data").path("status").asText());

        JsonNode reloaded = questions().path(0);
        assertEquals("APPROVED", reloaded.path("status").asText());

        JsonNode published = statusCommand(
            questionId,
            "PUBLISH",
            reloaded.path("state_version").asLong()
        );
        assertEquals("PUBLISHED", published.path("data").path("status").asText());
        assertEquals("PUBLISHED", questions().path(0).path("status").asText());
        assertEquals(
            "PUBLISHED",
            jdbcTemplate.queryForObject(
                "SELECT status FROM quiz_question_versions WHERE quiz_question_id = ?",
                String.class,
                UUID.fromString(questionId)
            )
        );
    }

    /**
     * state_version을 보내지 않아도 승인·게시가 되어야 한다.
     * 교사 화면은 불필요한 충돌 안내를 없애려고 이 값을 생략한다.
     */
    @Test
    void approvesAndPublishesWithoutAnExpectedStateVersion() throws Exception {
        generateDrafts(1);
        String questionId = questions().path(0).path("resource_id").asText();

        assertEquals(
            "APPROVED",
            statusCommand(questionId, "APPROVE", null).path("data").path("status").asText()
        );
        assertEquals(
            "PUBLISHED",
            statusCommand(questionId, "PUBLISH", null).path("data").path("status").asText()
        );
    }

    @Test
    void approvesEveryQuestionOfAMultiQuestionDraft() throws Exception {
        generateDrafts(3);

        JsonNode listed = questions();
        assertEquals(3, listed.size());
        for (JsonNode question : listed) {
            assertEquals("REVIEW_REQUIRED", question.path("status").asText());
            JsonNode approved = statusCommand(
                question.path("resource_id").asText(),
                "APPROVE",
                question.path("state_version").asLong()
            );
            assertEquals("APPROVED", approved.path("data").path("status").asText());
        }
        for (JsonNode question : questions()) {
            assertEquals("APPROVED", question.path("status").asText());
        }
    }

    private void generateDrafts(int count) throws Exception {
        mockMvc.perform(post(
                "/classrooms/{classroomId}/ai/quiz-draft-operations",
                classroomId
            )
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "grade": 5,
                      "topic": "용돈 관리",
                      "learning_objective": "용돈을 계획적으로 쓰는 방법을 안다",
                      "content_type": "ECONOMY",
                      "question_type": "MULTIPLE_CHOICE",
                      "question_count": %d
                    }
                    """.formatted(count)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.review_status").value("REVIEW_REQUIRED"));
    }

    private JsonNode questions() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/classrooms/{classroomId}/quiz-questions", classroomId).principal(owner)
            )
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
            .path("data")
            .path("questions");
    }

    private JsonNode statusCommand(
        String questionId,
        String command,
        Long expectedStateVersion
    ) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        if (expectedStateVersion != null) {
            body.put("expected_state_version", expectedStateVersion);
        }
        body.put("command", command);
        body.put("reason", "AI 초안의 문항·정답·해설을 교사가 검토함");
        MvcResult result = mockMvc.perform(post(
                "/classrooms/{classroomId}/quiz-questions/{questionId}/commands",
                classroomId,
                questionId
            )
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    static class FakeQuizClient implements QuizDraftAiClient {

        @Override
        public QuizDraftContract.Response draft(QuizDraftContract.Request request) {
            List<QuizDraftContract.QuestionDraft> questions = new ArrayList<>();
            for (int index = 1; index <= request.payload().count(); index++) {
                questions.add(new QuizDraftContract.QuestionDraft(
                    "REVIEW_REQUIRED",
                    request.payload().questionType(),
                    index + "번 문제입니다.",
                    List.of(
                        new QuizDraftContract.Choice("A", "첫 번째 보기"),
                        new QuizDraftContract.Choice("B", "두 번째 보기"),
                        new QuizDraftContract.Choice("C", "세 번째 보기")
                    ),
                    "A",
                    "해설입니다.",
                    request.payload().difficulty(),
                    List.of("용돈")
                ));
            }
            return new QuizDraftContract.Response(
                request.aiRunId(),
                QuizDraftContract.SCHEMA_VERSION,
                QuizDraftContract.PROMPT_VERSION,
                "FAKE",
                "fake-quiz-model",
                "PASSED",
                List.of(),
                new QuizDraftContract.Usage(10, 20),
                new QuizDraftContract.Output(
                    "REVIEW_REQUIRED",
                    request.payload().category(),
                    questions
                )
            );
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeQuizConfiguration {

        @Bean
        @Primary
        Clock fixedQuizClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        QuizDraftAiClient fakeQuizDraftAiClient() {
            return new FakeQuizClient();
        }
    }
}
