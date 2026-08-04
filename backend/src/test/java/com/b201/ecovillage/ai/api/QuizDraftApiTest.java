package com.b201.ecovillage.ai.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.ArrayList;
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

import com.b201.ecovillage.ai.contract.QuizDraftContract;
import com.b201.ecovillage.ai.domain.AiRun;
import com.b201.ecovillage.ai.infrastructure.AiServiceCallException;
import com.b201.ecovillage.ai.infrastructure.QuizDraftAiClient;
import com.b201.ecovillage.ai.repository.AiRunRepository;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomTeacher;
import com.b201.ecovillage.identity.domain.ClassroomTeacherRole;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomTeacherRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.quiz.application.QuizQuestionQueryService;
import com.b201.ecovillage.quiz.application.QuizQuestionView;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:quiz-draft;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "ecovillage.ai-monitor.enabled=false"
})
@AutoConfigureMockMvc
@Import(QuizDraftApiTest.FakeQuizConfiguration.class)
@Transactional
class QuizDraftApiTest {

    private static final String OWNER_SUBJECT = "quiz-draft-owner";
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
    private UserAccountRepository userRepository;

    @Autowired
    private AiRunRepository runRepository;

    @Autowired
    private QuizQuestionQueryService quizQuestionQueryService;

    @Autowired
    private RecordingQuizClient aiClient;

    private UUID classroomId;
    private UUID teacherId;
    private Principal owner;

    @BeforeEach
    void setUp() {
        aiClient.reset();
        classroomId = UUID.randomUUID();
        teacherId = UUID.randomUUID();
        owner = () -> OWNER_SUBJECT;
        userRepository.save(UserAccount.createTeacher(teacherId, OWNER_SUBJECT, "퀴즈 담임", NOW));
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
        entityManager.flush();
    }

    @Test
    void createsDraftQuestionsThroughTheQuizDomain() throws Exception {
        mockMvc.perform(draftRequest("MULTIPLE_CHOICE", 2))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.operation_id").isNotEmpty())
            .andExpect(jsonPath("$.data.status").value("RUNNING"))
            .andExpect(jsonPath("$.data.ai_run_id").isNotEmpty())
            .andExpect(jsonPath("$.data.review_status").value("REVIEW_REQUIRED"))
            .andExpect(jsonPath("$.data.question_ids.length()").value(2));

        entityManager.flush();
        List<QuizQuestionView> questions = quizQuestionQueryService.findAll(
            OWNER_SUBJECT,
            classroomId
        );
        assertEquals(2, questions.size());
        assertTrue(questions.stream().allMatch(question -> "REVIEW_REQUIRED".equals(question.status())));
        assertTrue(questions.stream().allMatch(question -> "AI".equals(question.source())));
        assertTrue(questions.stream().allMatch(question -> question.aiRunId() != null));
        assertTrue(questions.stream().allMatch(question -> question.options().size() == 3));
        assertTrue(questions.stream().allMatch(question -> question.options().stream()
            .filter(QuizQuestionView.Option::correct)
            .count() == 1));
        AiRun run = runRepository.findAll().getFirst();
        assertEquals("COMPLETED", run.getStatus());
        assertEquals("FAKE", run.getModelProvider());
        assertEquals(1, aiClient.calls);
    }

    @Test
    void mapsGradeToDifficultyForTheModel() throws Exception {
        mockMvc.perform(draftRequest("MULTIPLE_CHOICE", 1))
            .andExpect(status().isAccepted());

        assertEquals(
            QuizDraftContract.Difficulty.STANDARD,
            aiClient.lastRequest.payload().difficulty(),
            "초5는 STANDARD 난이도로 요청해야 합니다."
        );
        assertEquals(5, aiClient.lastRequest.grade());
    }

    @Test
    void rejectsWhenTheModelReturnsADifferentQuestionCount() throws Exception {
        aiClient.extraQuestion = true;

        mockMvc.perform(draftRequest("MULTIPLE_CHOICE", 2))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").value("AI_OUTPUT_CONTRACT_VIOLATION"));

        entityManager.flush();
        assertTrue(
            quizQuestionQueryService.findAll(OWNER_SUBJECT, classroomId).isEmpty(),
            "계약 위반 출력은 한 문항도 저장하지 않아야 합니다."
        );
    }

    @Test
    void rejectsWhenTheModelAnswerKeyIsMissingFromChoices() throws Exception {
        aiClient.brokenAnswerKey = true;

        mockMvc.perform(draftRequest("MULTIPLE_CHOICE", 1))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").value("AI_OUTPUT_CONTRACT_VIOLATION"));
    }

    @Test
    void failsWithoutSavingWhenFastApiIsDown() throws Exception {
        aiClient.fail = true;

        mockMvc.perform(draftRequest("MULTIPLE_CHOICE", 2))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").value("AI_TIMEOUT"));

        entityManager.flush();
        assertTrue(quizQuestionQueryService.findAll(OWNER_SUBJECT, classroomId).isEmpty());
    }

    @Test
    void rejectsWhenClassroomAiIsDisabled() throws Exception {
        mockMvc.perform(put("/classrooms/{classroomId}/ai-settings", classroomId)
                .principal(owner)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"enabled\": false, \"reason\": \"AI 중지\" }"))
            .andExpect(status().isOk());
        entityManager.flush();

        mockMvc.perform(draftRequest("MULTIPLE_CHOICE", 1))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("AI_DISABLED"));

        assertEquals(0, aiClient.calls);
    }

    @Test
    void rejectsWhenDailyLimitIsReached() throws Exception {
        for (int index = 0; index < 30; index++) {
            AiRun used = AiRun.generating(
                UUID.randomUUID(),
                classroomId,
                null,
                teacherId,
                "QUIZ_DRAFT",
                "TEACHER_REQUEST",
                QuizDraftContract.PROMPT_VERSION,
                java.util.Map.of(),
                NOW
            );
            used.completeWithModel("FAKE", "fake-quiz-model", java.util.Map.of(), NOW);
            runRepository.save(used);
        }
        entityManager.flush();

        mockMvc.perform(draftRequest("MULTIPLE_CHOICE", 1))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error.code").value("AI_DAILY_LIMIT_EXCEEDED"));

        assertEquals(0, aiClient.calls);
    }

    @Test
    void rejectsUnsupportedQuestionType() throws Exception {
        mockMvc.perform(draftRequest("SHORT_ANSWER", 1))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder draftRequest(
        String questionType,
        int count
    ) {
        return post("/classrooms/{classroomId}/ai/quiz-draft-operations", classroomId)
            .principal(owner)
            .header("Idempotency-Key", UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "grade": 5,
                  "topic": "용돈 관리",
                  "learning_objective": "용돈을 계획적으로 쓰는 방법을 안다",
                  "content_type": "ECONOMY",
                  "question_type": "%s",
                  "question_count": %d
                }
                """.formatted(questionType, count));
    }

    static class RecordingQuizClient implements QuizDraftAiClient {

        private int calls;
        private boolean fail;
        private boolean extraQuestion;
        private boolean brokenAnswerKey;
        private QuizDraftContract.Request lastRequest;

        void reset() {
            calls = 0;
            fail = false;
            extraQuestion = false;
            brokenAnswerKey = false;
            lastRequest = null;
        }

        @Override
        public QuizDraftContract.Response draft(QuizDraftContract.Request request) {
            calls++;
            lastRequest = request;
            if (fail) {
                throw new AiServiceCallException("AI_TIMEOUT", "simulated timeout");
            }
            int count = request.payload().count() + (extraQuestion ? 1 : 0);
            List<QuizDraftContract.QuestionDraft> questions = new ArrayList<>();
            for (int index = 1; index <= count; index++) {
                questions.add(new QuizDraftContract.QuestionDraft(
                    "REVIEW_REQUIRED",
                    request.payload().questionType(),
                    index + "번 문제입니다.",
                    List.of(
                        new QuizDraftContract.Choice("A", "첫 번째 보기"),
                        new QuizDraftContract.Choice("B", "두 번째 보기"),
                        new QuizDraftContract.Choice("C", "세 번째 보기")
                    ),
                    brokenAnswerKey ? "E" : "A",
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
        RecordingQuizClient recordingQuizClient() {
            return new RecordingQuizClient();
        }
    }
}
