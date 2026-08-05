package com.b201.ecovillage.quiz.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomTeacher;
import com.b201.ecovillage.identity.domain.ClassroomTeacherRole;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.ClassroomTeacherRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

/**
 * 문항 상태 전이(APPROVE/PUBLISH)의 expected_state_version은 문항의 state_version이다.
 * 이전에는 학급의 state_version과 비교해 AI 초안 승인이 항상 409로 거절되었다.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:quiz-question-status;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.task.scheduling.enabled=false",
    "ecovillage.ai-monitor.enabled=false"
})
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class QuizQuestionStatusApiTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-03T01:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private ClassroomRepository classroomRepository;
    @Autowired private ClassroomTeacherRepository teacherRepository;
    @Autowired private ClassroomStudentRepository studentRepository;

    private UUID classroomId;
    private UUID studentId;
    private Principal owner;

    @BeforeEach
    void setUp() {
        classroomId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        owner = () -> "quiz-question-owner-" + classroomId;

        classroomRepository.save(Classroom.active(
            classroomId,
            randomClassroomCode(),
            CREATED_AT
        ));
        userRepository.save(UserAccount.createTeacher(
            teacherId,
            owner.getName(),
            "Quiz Owner",
            CREATED_AT
        ));
        teacherRepository.save(ClassroomTeacher.assign(
            UUID.randomUUID(),
            classroomId,
            teacherId,
            ClassroomTeacherRole.OWNER,
            CREATED_AT
        ));
        studentRepository.save(ClassroomStudent.enroll(
            studentId,
            classroomId,
            1,
            "student",
            CREATED_AT
        ));
    }

    @Test
    void approvesAiReviewRequiredQuestionUsingItsOwnStateVersion() throws Exception {
        UUID questionId = createQuestion();
        // AI 초안 생성(QuizDraftService)이 남기는 상태를 그대로 재현한다.
        // 문항의 state_version이 학급의 state_version과 달라지는 지점이다.
        markAsAiReviewRequired(questionId);

        long reviewStateVersion = questionStateVersion(questionId);
        assertEquals(1L, reviewStateVersion);
        assertEquals(0L, classroomStateVersion());

        JsonNode approved = statusCommand(
            questionId,
            UUID.randomUUID(),
            "APPROVE",
            reviewStateVersion
        );
        assertEquals("APPROVED", approved.path("data").path("status").asText());
        assertEquals(2L, approved.path("data").path("state_version").asLong());
    }

    @Test
    void rejectsStatusChangeWhenTheQuestionStateVersionIsStale() throws Exception {
        UUID questionId = createQuestion();
        long draftStateVersion = questionStateVersion(questionId);

        statusCommand(questionId, UUID.randomUUID(), "APPROVE", draftStateVersion);

        // 승인으로 문항 버전이 올라갔으므로 예전 버전으로 게시하면 거절되어야 한다.
        mockMvc.perform(statusRequest(
                questionId,
                UUID.randomUUID(),
                "PUBLISH",
                draftStateVersion
            ))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("STATE_VERSION_CONFLICT"));
    }

    @Test
    void runsApproveThenPublishAndExposesTheResultToTheQuestionBank() throws Exception {
        UUID questionId = createQuestion();

        JsonNode approved = statusCommand(
            questionId,
            UUID.randomUUID(),
            "APPROVE",
            questionStateVersion(questionId)
        );
        assertEquals("APPROVED", approved.path("data").path("status").asText());

        UUID publishKey = UUID.randomUUID();
        long approvedStateVersion = approved.path("data").path("state_version").asLong();
        JsonNode published = statusCommand(
            questionId,
            publishKey,
            "PUBLISH",
            approvedStateVersion
        );
        assertEquals("PUBLISHED", published.path("data").path("status").asText());

        JsonNode replayed = statusCommand(
            questionId,
            publishKey,
            "PUBLISH",
            approvedStateVersion
        );
        assertEquals(published.path("data"), replayed.path("data"));

        mockMvc.perform(get("/classrooms/{classroomId}/quiz-questions", classroomId)
                .principal(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.questions[0].resource_id")
                .value(questionId.toString()))
            .andExpect(jsonPath("$.data.questions[0].status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.questions[0].state_version")
                .value(published.path("data").path("state_version").asLong()));
    }

    /**
     * 교사 화면은 문제은행 조회 응답의 question_version_id로 퀴즈 세션을 시작한다.
     * 이 값이 빠지면 승인·게시한 문항을 수업에 쓸 방법이 없다.
     */
    @Test
    void startsAGameQuizSessionWithThePublishedQuestionVersionFromTheQuestionBank()
        throws Exception {
        UUID questionId = createQuestion();
        JsonNode approved = statusCommand(
            questionId,
            UUID.randomUUID(),
            "APPROVE",
            questionStateVersion(questionId)
        );
        statusCommand(
            questionId,
            UUID.randomUUID(),
            "PUBLISH",
            approved.path("data").path("state_version").asLong()
        );

        MvcResult listed = mockMvc.perform(
                get("/classrooms/{classroomId}/quiz-questions", classroomId)
                    .principal(owner)
            )
            .andExpect(status().isOk())
            .andReturn();
        JsonNode question = objectMapper
            .readTree(listed.getResponse().getContentAsByteArray())
            .path("data")
            .path("questions")
            .path(0);
        String questionVersionId = question.path("question_version_id").asText();
        assertNotEquals("", questionVersionId, "question_version_id must be exposed");
        assertEquals("PUBLISHED", question.path("status").asText());

        UUID sessionId = UUID.randomUUID();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("expected_state_version", 0);
        body.put("command", "START");
        body.putArray("question_version_ids").add(questionVersionId);
        body.putArray("participant_ids").add(studentId.toString());
        body.put("mode", "GAME");
        body.put("content_type", "ECONOMY");
        body.putObject("reward_policy_public").put("reward_per_person", 50);
        body.put("leaderboard_public", true);

        UUID startKey = UUID.randomUUID();
        MvcResult started = mockMvc.perform(post(
                "/classrooms/{classroomId}/quiz-sessions/{sessionId}/commands",
                classroomId,
                sessionId
            )
                .principal(owner)
                .header("Idempotency-Key", startKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.quiz_resource_id").value(sessionId.toString()))
            .andExpect(jsonPath("$.data.status").value("OPEN"))
            .andReturn();

        // 같은 키로 다시 보내면 같은 결과를 돌려주고 세션이 하나만 남아야 한다.
        JsonNode first = objectMapper.readTree(
            started.getResponse().getContentAsByteArray()
        );
        MvcResult replayed = mockMvc.perform(post(
                "/classrooms/{classroomId}/quiz-sessions/{sessionId}/commands",
                classroomId,
                sessionId
            )
                .principal(owner)
                .header("Idempotency-Key", startKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()))
            .andExpect(status().isOk())
            .andReturn();
        assertEquals(
            first.path("data"),
            objectMapper.readTree(replayed.getResponse().getContentAsByteArray()).path("data")
        );
        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM quiz_sessions WHERE classroom_id = ?",
                Integer.class,
                classroomId
            )
        );
        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM quiz_session_questions WHERE quiz_session_id = ?",
                Integer.class,
                sessionId
            )
        );
    }

    @Test
    void refusesToStartASessionWithAQuestionThatIsNotPublished() throws Exception {
        UUID questionId = createQuestion();
        MvcResult listed = mockMvc.perform(
                get("/classrooms/{classroomId}/quiz-questions", classroomId)
                    .principal(owner)
            )
            .andExpect(status().isOk())
            .andReturn();
        String questionVersionId = objectMapper
            .readTree(listed.getResponse().getContentAsByteArray())
            .path("data").path("questions").path(0)
            .path("question_version_id").asText();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("expected_state_version", 0);
        body.put("command", "START");
        body.putArray("question_version_ids").add(questionVersionId);
        body.putArray("participant_ids").add(studentId.toString());
        body.put("mode", "GAME");
        body.put("content_type", "ECONOMY");
        body.putObject("reward_policy_public").put("reward_per_person", 50);
        body.put("leaderboard_public", true);

        mockMvc.perform(post(
                "/classrooms/{classroomId}/quiz-sessions/{sessionId}/commands",
                classroomId,
                UUID.randomUUID()
            )
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("QUESTION_NOT_PUBLISHED"));
    }

    private UUID createQuestion() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("expected_state_version", classroomStateVersion());
        body.put("command", "CREATE");
        body.put("content_type", "ECONOMY");
        body.put("question_type", "MULTIPLE_CHOICE");
        body.put("prompt", "소득세는 어디에 모이나요?");
        body.put("explanation", "소득세는 공동기금으로 모입니다.");
        ArrayNode options = body.putArray("options");
        ObjectNode wrong = options.addObject();
        wrong.put("option_key", "A");
        wrong.put("option_text", "내 보관함");
        wrong.put("is_correct", false);
        wrong.put("sort_order", 1);
        ObjectNode correct = options.addObject();
        correct.put("option_key", "B");
        correct.put("option_text", "우리 반 공동기금");
        correct.put("is_correct", true);
        correct.put("sort_order", 2);

        MvcResult result = mockMvc.perform(post(
                "/classrooms/{classroomId}/quiz-question-commands",
                classroomId
            )
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andReturn();
        JsonNode created = objectMapper.readTree(
            result.getResponse().getContentAsByteArray()
        );
        return UUID.fromString(
            created.path("data").path("quiz_resource_id").asText()
        );
    }

    private JsonNode statusCommand(
        UUID questionId,
        UUID idempotencyKey,
        String command,
        long expectedStateVersion
    ) throws Exception {
        MvcResult result = mockMvc.perform(statusRequest(
                questionId,
                idempotencyKey,
                command,
                expectedStateVersion
            ))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
        statusRequest(
            UUID questionId,
            UUID idempotencyKey,
            String command,
            long expectedStateVersion
        ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("expected_state_version", expectedStateVersion);
        body.put("command", command);
        body.put("reason", "교사가 문항과 정답, 해설을 검토함");
        return post(
            "/classrooms/{classroomId}/quiz-questions/{questionId}/commands",
            classroomId,
            questionId
        )
            .principal(owner)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body.toString());
    }

    private void markAsAiReviewRequired(UUID questionId) {
        jdbcTemplate.update(
            "UPDATE quiz_questions SET status = 'REVIEW_REQUIRED',"
                + " state_version = state_version + 1 WHERE id = ?",
            questionId
        );
        jdbcTemplate.update(
            "UPDATE quiz_question_versions SET status = 'REVIEW_REQUIRED',"
                + " source = 'AI' WHERE quiz_question_id = ?",
            questionId
        );
    }

    private long questionStateVersion(UUID questionId) {
        Long value = jdbcTemplate.queryForObject(
            "SELECT state_version FROM quiz_questions WHERE id = ?",
            Long.class,
            questionId
        );
        return value == null ? 0L : value;
    }

    private long classroomStateVersion() {
        Long value = jdbcTemplate.queryForObject(
            "SELECT state_version FROM classrooms WHERE id = ?",
            Long.class,
            classroomId
        );
        return value == null ? 0L : value;
    }

    private static String randomClassroomCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
