package com.b201.ecovillage.quiz.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import tools.jackson.databind.node.ObjectNode;

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
import com.b201.ecovillage.ledger.application.AtomicLedgerTransactionService;
import com.b201.ecovillage.ledger.application.LedgerTransactionCommand;
import com.b201.ecovillage.ledger.application.LedgerTransactionPosting;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.LedgerActorType;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.quiz.application.QuizRewardBudget;
import com.b201.ecovillage.quiz.application.QuizRewardBudgetService;
import com.b201.ecovillage.quiz.domain.ActivityParticipant;
import com.b201.ecovillage.quiz.domain.ActivitySession;
import com.b201.ecovillage.quiz.domain.QuizAnswer;
import com.b201.ecovillage.quiz.domain.QuizContentType;
import com.b201.ecovillage.quiz.domain.QuizMode;
import com.b201.ecovillage.quiz.domain.QuizOption;
import com.b201.ecovillage.quiz.domain.QuizSession;
import com.b201.ecovillage.quiz.domain.QuizSessionQuestion;
import com.b201.ecovillage.quiz.domain.QuizSubmission;
import com.b201.ecovillage.quiz.repository.ActivityParticipantRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;
import com.b201.ecovillage.quiz.repository.QuizAnswerRepository;
import com.b201.ecovillage.quiz.repository.QuizOptionRepository;
import com.b201.ecovillage.quiz.repository.QuizSessionQuestionRepository;
import com.b201.ecovillage.quiz.repository.QuizSessionRepository;
import com.b201.ecovillage.quiz.repository.QuizSubmissionRepository;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:quiz-result-workflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class QuizResultWorkflowApiTest {

    private static final Instant NOW = Instant.parse("2026-08-03T03:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private ClassroomRepository classroomRepository;
    @Autowired private ClassroomTeacherRepository teacherRepository;
    @Autowired private ClassroomStudentRepository studentRepository;
    @Autowired private ActivitySessionRepository activityRepository;
    @Autowired private ActivityParticipantRepository participantRepository;
    @Autowired private QuizSessionRepository sessionRepository;
    @Autowired private QuizSessionQuestionRepository sessionQuestionRepository;
    @Autowired private QuizSubmissionRepository submissionRepository;
    @Autowired private QuizAnswerRepository answerRepository;
    @Autowired private QuizOptionRepository optionRepository;
    @Autowired private LedgerAccountRepository ledgerAccountRepository;
    @Autowired private AtomicLedgerTransactionService transactionService;
    @Autowired private QuizRewardBudgetService rewardBudgetService;

    private UUID classroomId;
    private UUID sessionId;
    private UUID teacherId;
    private UUID firstStudentId;
    private UUID secondStudentId;
    private UUID questionId;
    private Principal teacher;
    private Principal firstStudent;

    @BeforeEach
    void setUp() {
        classroomId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        teacherId = UUID.randomUUID();
        firstStudentId = UUID.randomUUID();
        secondStudentId = UUID.randomUUID();
        questionId = UUID.randomUUID();
        teacher = () -> "quiz-result-teacher-" + classroomId;
        firstStudent = () -> firstStudentId.toString();

        userRepository.save(UserAccount.createTeacher(
            teacherId,
            teacher.getName(),
            "Quiz Teacher",
            NOW
        ));
        classroomRepository.save(Classroom.create(
            classroomId,
            UUID.randomUUID(),
            classroomCode(),
            "ECO",
            5,
            CourseSize.ONE_MONTH,
            2,
            LocalDate.parse("2026-08-01"),
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
        studentRepository.saveAll(List.of(
            ClassroomStudent.enroll(firstStudentId, classroomId, 1, "alpha", NOW),
            ClassroomStudent.enroll(secondStudentId, classroomId, 2, "beta", NOW)
        ));
        seedScoringQuiz(QuizMode.GAME, Map.of("reward_per_person", 100));
        seedIssuance(classroomId, firstStudentId, 10_000L);
    }

    @Test
    void confirmsPublishesRewardsOnceReportsAndRegradesWithRecovery() throws Exception {
        mockMvc.perform(get("/classrooms/{classroomId}/quiz-sessions", classroomId)
                .principal(teacher))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sessions[0].quiz_session_id").value(sessionId.toString()))
            .andExpect(jsonPath("$.data.sessions[0].status").value("SCORING"))
            .andExpect(jsonPath("$.data.sessions[0].content_type").value("ECONOMY"))
            .andExpect(jsonPath("$.data.sessions[0].questions[0].prompt").isNotEmpty());

        UUID confirmKey = UUID.randomUUID();
        JsonNode confirmed = commandResult(confirmKey, body("CONFIRM", 0, null, null));
        assertEquals("REVIEWING", confirmed.path("data").path("status").asText());
        long reviewingStateVersion = confirmed.path("data").path("state_version").asLong();

        mockMvc.perform(get(
                "/classrooms/{classroomId}/quiz-sessions/{sessionId}/results",
                classroomId,
                sessionId
            ).principal(firstStudent))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PUBLIC_DATA_ONLY"));

        UUID publishKey = UUID.randomUUID();
        ObjectNode publishBody = body("PUBLISH", reviewingStateVersion, 1, true);
        JsonNode published = commandResult(publishKey, publishBody);
        assertEquals("RESULT_PUBLISHED", published.path("data").path("status").asText());
        long publishedStateVersion = published.path("data").path("state_version").asLong();

        JsonNode replayed = commandResult(publishKey, publishBody);
        assertEquals(published.path("data"), replayed.path("data"));
        assertEquals(1, count("transaction_groups", "transaction_type = 'QUIZ_REWARD'"));
        // 보상은 문제당 보상액 × 맞힌 문제 수라 오답만 낸 학생은 받지 못한다.
        assertEquals(10_100L, walletBalance(firstStudentId));
        assertEquals(0L, walletBalance(secondStudentId));

        mockMvc.perform(get(
                "/classrooms/{classroomId}/quiz-sessions/{sessionId}/results",
                classroomId,
                sessionId
            ).principal(firstStudent))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.result_version").value(1))
            .andExpect(jsonPath("$.data.summary.score").value(10))
            .andExpect(jsonPath("$.data.summary.reward_amount").value(100))
            .andExpect(jsonPath("$.data.leaderboard.length()").value(2))
            .andExpect(jsonPath("$.data.questions.length()").value(1))
            .andExpect(jsonPath("$.data.questions[0].prompt").value("What is saving?"))
            .andExpect(jsonPath("$.data.questions[0].selected_option_text").value("Keep money"))
            .andExpect(jsonPath("$.data.questions[0].correct_option_text").value("Keep money"))
            .andExpect(jsonPath("$.data.questions[0].correct").value(true));

        mockMvc.perform(get(
                "/classrooms/{classroomId}/quiz-sessions/{sessionId}/results",
                classroomId,
                sessionId
            ).principal(() -> secondStudentId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary.score").value(0))
            .andExpect(jsonPath("$.data.summary.correct_count").value(0))
            .andExpect(jsonPath("$.data.questions[0].selected_option_text").value("Spend everything"))
            .andExpect(jsonPath("$.data.questions[0].correct_option_text").value("Keep money"))
            .andExpect(jsonPath("$.data.questions[0].correct").value(false));

        UUID reportKey = UUID.randomUUID();
        ObjectNode report = objectMapper.createObjectNode();
        report.put("expected_state_version", publishedStateVersion);
        report.put("question_id", questionId.toString());
        report.put("reason_code", "AMBIGUOUS_ANSWER");
        report.put("description", "Two choices can be interpreted as correct.");
        MvcResult reported = mockMvc.perform(post(
                "/classrooms/{classroomId}/quiz-sessions/{sessionId}/question-issue-reports",
                classroomId,
                sessionId
            )
                .principal(firstStudent)
                .header("Idempotency-Key", reportKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(report.toString()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("UNDER_REVIEW"))
            .andReturn();

        mockMvc.perform(post(
                "/classrooms/{classroomId}/quiz-sessions/{sessionId}/question-issue-reports",
                classroomId,
                sessionId
            )
                .principal(firstStudent)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(report.toString()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("DUPLICATE_REPORT"));

        UUID nonExamineeId = UUID.randomUUID();
        studentRepository.save(ClassroomStudent.enroll(
            nonExamineeId,
            classroomId,
            3,
            "non-examinee",
            NOW
        ));
        mockMvc.perform(post(
                "/classrooms/{classroomId}/quiz-sessions/{sessionId}/question-issue-reports",
                classroomId,
                sessionId
            )
                .principal(() -> nonExamineeId.toString())
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(report.toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("NOT_EXAMINEE"));

        UUID regradeKey = UUID.randomUUID();
        ObjectNode regrade = objectMapper.createObjectNode();
        regrade.put("expected_state_version", publishedStateVersion);
        regrade.put("expected_result_version", 1);
        regrade.putArray("invalidated_question_ids").add(questionId.toString());
        regrade.put("reason", "The reported question is invalid.");
        MvcResult accepted = mockMvc.perform(post(
                "/classrooms/{classroomId}/quiz-sessions/{sessionId}/regrade-operations",
                classroomId,
                sessionId
            )
                .principal(teacher)
                .header("Idempotency-Key", regradeKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(regrade.toString()))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.status").value("RUNNING"))
            .andReturn();
        String operationId = objectMapper.readTree(accepted.getResponse().getContentAsByteArray())
            .path("data").path("operation_id").asText();

        mockMvc.perform(post(
                "/classrooms/{classroomId}/quiz-sessions/{sessionId}/regrade-operations",
                classroomId,
                sessionId
            )
                .principal(teacher)
                .header("Idempotency-Key", regradeKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(regrade.toString()))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.operation_id").value(operationId));

        assertEquals(1, count("regrading_runs", "1 = 1"));
        assertEquals(2, count("quiz_result_versions", "1 = 1"));
        // 유일한 문항이 무효 처리되면 맞힌 문제가 없어 재지급 거래가 생기지 않는다.
        assertEquals(1, count("transaction_groups", "transaction_type = 'QUIZ_REWARD'"));
        assertEquals(1, count(
            "transaction_groups",
            "transaction_type = 'RECOVERY_QUIZ_REGRADING'"
        ));
        assertEquals(10_000L, walletBalance(firstStudentId));
        assertEquals(0L, walletBalance(secondStudentId));
        assertEquals(
            0L,
            rewardBudgetService.evaluate(classroomId, 0L).existingRewardIssuance()
        );

        mockMvc.perform(get(
                "/classrooms/{classroomId}/quiz-sessions/{sessionId}/results",
                classroomId,
                sessionId
            ).principal(firstStudent))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.result_version").value(2))
            .andExpect(jsonPath("$.data.summary.score").value(0))
            .andExpect(jsonPath("$.data.summary.invalidated_question_count").value(1));

        commandResult(
            UUID.randomUUID(),
            body("CLOSE", publishedStateVersion, 2, null)
        );
        assertEquals("CLOSED", sessionRepository.findById(sessionId).orElseThrow()
            .getStatus().name());
    }

    @Test
    void concurrentPublishRetriesPayRewardsOnlyOnce() throws Exception {
        JsonNode confirmed = commandResult(
            UUID.randomUUID(),
            body("CONFIRM", 0, null, null)
        );
        long stateVersion = confirmed.path("data").path("state_version").asLong();
        UUID publishKey = UUID.randomUUID();
        ObjectNode publishBody = body("PUBLISH", stateVersion, 1, true);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> {
                start.await();
                return mockMvc.perform(resultCommandRequest(
                    classroomId,
                    sessionId,
                    publishKey,
                    publishBody
                )).andReturn().getResponse().getStatus();
            });
            Future<Integer> second = executor.submit(() -> {
                start.await();
                return mockMvc.perform(resultCommandRequest(
                    classroomId,
                    sessionId,
                    publishKey,
                    publishBody
                )).andReturn().getResponse().getStatus();
            });
            start.countDown();

            assertEquals(200, first.get(10, TimeUnit.SECONDS));
            assertEquals(200, second.get(10, TimeUnit.SECONDS));
        }

        assertEquals(1, count("transaction_groups", "transaction_type = 'QUIZ_REWARD'"));
        assertEquals(10_100L, walletBalance(firstStudentId));
        assertEquals(0L, walletBalance(secondStudentId));
    }

    @Test
    void rejectsTestRewardsAndPublicLeaderboards() throws Exception {
        UUID testClassroom = UUID.randomUUID();
        UUID testSession = UUID.randomUUID();
        UUID testStudent = UUID.randomUUID();
        Classroom classroom = Classroom.create(
            testClassroom,
            UUID.randomUUID(),
            classroomCode(),
            "TEST",
            5,
            CourseSize.ONE_MONTH,
            1,
            LocalDate.parse("2026-08-01"),
            LocalDate.parse("2026-08-31"),
            "Asia/Seoul",
            NOW
        );
        classroomRepository.save(classroom);
        teacherRepository.save(ClassroomTeacher.assign(
            UUID.randomUUID(),
            testClassroom,
            teacherId,
            ClassroomTeacherRole.OWNER,
            NOW
        ));
        studentRepository.save(ClassroomStudent.enroll(
            testStudent,
            testClassroom,
            1,
            "test-student",
            NOW
        ));
        seedScoringQuiz(
            testClassroom,
            testSession,
            testStudent,
            QuizMode.TEST,
            Map.of("reward_per_person", 1)
        );

        mockMvc.perform(resultCommandRequest(
                testClassroom,
                testSession,
                UUID.randomUUID(),
                body("CONFIRM", 0, null, null)
            ))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN_REWARD_CONTEXT"));

        UUID zeroRewardSession = UUID.randomUUID();
        seedScoringQuiz(
            testClassroom,
            zeroRewardSession,
            testStudent,
            QuizMode.TEST,
            Map.of()
        );
        UUID draftStudent = UUID.randomUUID();
        studentRepository.save(ClassroomStudent.enroll(
            draftStudent,
            testClassroom,
            2,
            "draft-student",
            NOW
        ));
        participantRepository.save(ActivityParticipant.joined(
            UUID.randomUUID(), testClassroom, zeroRewardSession, draftStudent, NOW
        ));
        UUID zeroRewardQuestion = sessionQuestionRepository
            .findAllByClassroomIdAndQuizSessionIdOrderBySequenceNumber(
                testClassroom,
                zeroRewardSession
            )
            .getFirst()
            .getId();
        QuizSubmission draft = submissionRepository.save(QuizSubmission.create(
            UUID.randomUUID(),
            testClassroom,
            zeroRewardSession,
            draftStudent,
            QuizMode.TEST,
            1,
            false,
            NOW
        ));
        answerRepository.save(QuizAnswer.selected(
            UUID.randomUUID(),
            testClassroom,
            draft.getId(),
            zeroRewardQuestion,
            "A",
            true,
            10,
            1_000,
            NOW
        ));
        JsonNode confirmed = response(resultCommandRequest(
            testClassroom,
            zeroRewardSession,
            UUID.randomUUID(),
            body("CONFIRM", 0, null, null)
        ));
        long stateVersion = confirmed.path("data").path("state_version").asLong();
        // 교사가 제출을 마감한 시점의 답안지가 곧 걷은 시험지다. 학생이 "퀴즈 마치기"를 누르지
        // 못해 임시 저장으로 남았더라도 맞힌 문항은 그대로 점수가 된다.
        assertEquals(10, jdbcTemplate.queryForObject(
            """
                SELECT student_result.score
                  FROM quiz_student_results student_result
                  JOIN quiz_result_versions result_version
                    ON result_version.id = student_result.quiz_result_version_id
                 WHERE result_version.classroom_id = ?
                   AND result_version.quiz_session_id = ?
                   AND student_result.classroom_student_id = ?
                """,
            Integer.class,
            testClassroom,
            zeroRewardSession,
            draftStudent
        ));

        mockMvc.perform(resultCommandRequest(
                testClassroom,
                zeroRewardSession,
                UUID.randomUUID(),
                body("PUBLISH", stateVersion, 1, true)
            ))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("PUBLIC_DATA_ONLY"));
    }

    @Test
    void appliesRewardWarningAndBlockingBoundaries() {
        UUID atTwentyFive = budgetClassroom(75L);
        UUID aboveTwentyFive = budgetClassroom(74L);
        UUID atThirty = budgetClassroom(70L);
        UUID aboveThirty = budgetClassroom(69L);
        UUID repeatingShare = budgetClassroom(5L);

        QuizRewardBudget exactWarn = rewardBudgetService.evaluate(atTwentyFive, 25L);
        QuizRewardBudget warning = rewardBudgetService.evaluate(aboveTwentyFive, 26L);
        QuizRewardBudget exactMax = rewardBudgetService.evaluate(atThirty, 30L);
        QuizRewardBudget blocked = rewardBudgetService.evaluate(aboveThirty, 31L);
        QuizRewardBudget rounded = rewardBudgetService.evaluate(repeatingShare, 2L);

        assertEquals(2_500, exactWarn.rewardShareBasisPoints());
        assertFalse(exactWarn.warning());
        assertFalse(exactWarn.blocked());
        assertTrue(warning.warning());
        assertFalse(warning.blocked());
        assertEquals(3_000, exactMax.rewardShareBasisPoints());
        assertTrue(exactMax.warning());
        assertFalse(exactMax.blocked());
        assertFalse(blocked.warning());
        assertTrue(blocked.blocked());
        assertEquals(2_857, rounded.rewardShareBasisPoints());
        assertTrue(rounded.warning());
        assertFalse(rounded.blocked());
    }

    @Test
    void letsTeacherAcknowledgeRewardShareWarningAndPublish() throws Exception {
        jdbcTemplate.update(
            "update transaction_groups set transaction_type='QUIZ_REWARD' where classroom_id=? and transaction_type='BASE_ISSUANCE'",
            classroomId
        );
        JsonNode confirmed = commandResult(
            UUID.randomUUID(),
            body("CONFIRM", 0, null, null)
        );
        long reviewingStateVersion = confirmed.path("data").path("state_version").asLong();
        ObjectNode publish = body("PUBLISH", reviewingStateVersion, 1, true);

        mockMvc.perform(resultCommandRequest(
                classroomId,
                sessionId,
                UUID.randomUUID(),
                publish
            ))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("REWARD_ISSUANCE_SHARE_EXCEEDED"));

        publish.put("reward_warning_acknowledged", true);
        JsonNode published = commandResult(UUID.randomUUID(), publish);
        assertEquals("RESULT_PUBLISHED", published.path("data").path("status").asText());
    }

    @Test
    void letsGameStudentChangeAnswerUntilFinalSubmission() throws Exception {
        UUID openSessionId = UUID.randomUUID();
        UUID openQuestionId = UUID.randomUUID();
        UUID questionVersionId = UUID.randomUUID();
        UUID optionAId = UUID.randomUUID();
        UUID optionBId = UUID.randomUUID();

        activityRepository.save(ActivitySession.liveQuiz(
            openSessionId, classroomId, teacherId, true, NOW
        ));
        sessionRepository.save(QuizSession.open(
            openSessionId,
            classroomId,
            QuizMode.GAME,
            QuizContentType.ECONOMY,
            Map.of("reward_per_person", 100),
            false,
            NOW
        ));
        sessionQuestionRepository.save(QuizSessionQuestion.snapshot(
            openQuestionId,
            classroomId,
            openSessionId,
            questionVersionId,
            1,
            "Which answer can still be changed?",
            List.of(
                Map.of("option_key", "A", "option_text", "First"),
                Map.of("option_key", "B", "option_text", "Second")
            ),
            "A",
            10,
            30,
            NOW
        ));
        optionRepository.saveAll(List.of(
            QuizOption.create(
                optionAId, classroomId, questionVersionId, "A", "First", true, 1, NOW
            ),
            QuizOption.create(
                optionBId, classroomId, questionVersionId, "B", "Second", false, 2, NOW
            )
        ));
        participantRepository.save(ActivityParticipant.joined(
            UUID.randomUUID(), classroomId, openSessionId, firstStudentId, NOW
        ));

        saveAnswerSheet(openSessionId, openQuestionId, optionAId, 0, false)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.state_version").value(1));
        saveAnswerSheet(openSessionId, openQuestionId, optionBId, 1, false)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.state_version").value(2));

        QuizSubmission changed = submissionRepository
            .findTopByClassroomIdAndQuizSessionIdAndClassroomStudentIdAndModeOrderByAnswerSheetVersionDesc(
                classroomId, openSessionId, firstStudentId, QuizMode.GAME
            )
            .orElseThrow();
        assertEquals(
            "B",
            answerRepository.findAllByClassroomIdAndQuizSubmissionIdIn(
                classroomId, List.of(changed.getId())
            ).getFirst().getSelectedOptionKey()
        );

        saveAnswerSheet(openSessionId, openQuestionId, optionAId, 2, true)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
            .andExpect(jsonPath("$.data.state_version").value(3));
        saveAnswerSheet(openSessionId, openQuestionId, optionBId, 3, false)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("SUBMISSION_ALREADY_FINAL"));
    }

    @Test
    void recordsFinalSubmissionEvenWhenStudentLeavesEveryAnswerBlank() throws Exception {
        UUID openSessionId = UUID.randomUUID();
        activityRepository.save(ActivitySession.liveQuiz(
            openSessionId, classroomId, teacherId, true, NOW
        ));
        sessionRepository.save(QuizSession.open(
            openSessionId,
            classroomId,
            QuizMode.GAME,
            QuizContentType.ECONOMY,
            Map.of("reward_per_person", 100),
            false,
            NOW
        ));
        participantRepository.save(ActivityParticipant.joined(
            UUID.randomUUID(), classroomId, openSessionId, firstStudentId, NOW
        ));

        ObjectNode body = objectMapper.createObjectNode();
        body.put("answer_sheet_version", 0);
        body.put("submit", true);
        body.putArray("answers");
        mockMvc.perform(put(
                "/classrooms/{classroomId}/quiz-sessions/{sessionId}/test-answer-sheet",
                classroomId,
                openSessionId
            )
            .principal(firstStudent)
            .header("Idempotency-Key", UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
            .andExpect(jsonPath("$.data.state_version").value(1));
    }

    private org.springframework.test.web.servlet.ResultActions saveAnswerSheet(
        UUID targetSession,
        UUID targetQuestion,
        UUID targetOption,
        int answerSheetVersion,
        boolean submit
    ) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("expected_state_version", 0);
        body.put("answer_sheet_version", answerSheetVersion);
        body.put("submit", submit);
        ObjectNode answer = objectMapper.createObjectNode();
        answer.put("question_id", targetQuestion.toString());
        answer.put("option_id", targetOption.toString());
        body.putArray("answers").add(answer);
        return mockMvc.perform(put(
                "/classrooms/{classroomId}/quiz-sessions/{sessionId}/test-answer-sheet",
                classroomId,
                targetSession
            )
            .principal(firstStudent)
            .header("Idempotency-Key", UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body.toString()));
    }

    private void seedScoringQuiz(QuizMode mode, Map<String, Object> rewardPolicy) {
        seedScoringQuiz(
            classroomId,
            sessionId,
            firstStudentId,
            mode,
            rewardPolicy
        );
        participantRepository.save(ActivityParticipant.joined(
            UUID.randomUUID(), classroomId, sessionId, secondStudentId, NOW
        ));
        QuizSubmission secondSubmission = submissionRepository.save(QuizSubmission.create(
            UUID.randomUUID(), classroomId, sessionId, secondStudentId, mode, 1, true, NOW
        ));
        answerRepository.save(QuizAnswer.selected(
            UUID.randomUUID(), classroomId, secondSubmission.getId(), questionId,
            "B", false, 0, 2_000, NOW
        ));
    }

    private void seedScoringQuiz(
        UUID targetClassroom,
        UUID targetSession,
        UUID targetStudent,
        QuizMode mode,
        Map<String, Object> rewardPolicy
    ) {
        ActivitySession activity = ActivitySession.liveQuiz(
            targetSession, targetClassroom, teacherId, true, NOW
        );
        activity.beginFinalizing(NOW.plusSeconds(60));
        activityRepository.save(activity);
        QuizSession session = QuizSession.open(
            targetSession,
            targetClassroom,
            mode,
            QuizContentType.ECONOMY,
            rewardPolicy,
            false,
            NOW
        );
        session.closeSubmissions(NOW.plusSeconds(60));
        sessionRepository.save(session);
        UUID targetQuestion = targetSession.equals(sessionId) ? questionId : UUID.randomUUID();
        sessionQuestionRepository.save(QuizSessionQuestion.snapshot(
            targetQuestion,
            targetClassroom,
            targetSession,
            UUID.randomUUID(),
            1,
            "What is saving?",
            List.of(
                Map.of("option_key", "A", "option_text", "Keep money"),
                Map.of("option_key", "B", "option_text", "Spend everything")
            ),
            "A",
            10,
            30,
            NOW
        ));
        participantRepository.save(ActivityParticipant.joined(
            UUID.randomUUID(), targetClassroom, targetSession, targetStudent, NOW
        ));
        QuizSubmission submission = submissionRepository.save(QuizSubmission.create(
            UUID.randomUUID(), targetClassroom, targetSession, targetStudent, mode, 1, true, NOW
        ));
        answerRepository.save(QuizAnswer.selected(
            UUID.randomUUID(), targetClassroom, submission.getId(), targetQuestion,
            "A", true, 10, 1_000, NOW
        ));
    }

    private UUID budgetClassroom(long nonRewardIssuance) {
        UUID id = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        classroomRepository.save(Classroom.create(
            id,
            UUID.randomUUID(),
            classroomCode(),
            "BUDGET",
            5,
            CourseSize.ONE_MONTH,
            1,
            LocalDate.parse("2026-08-01"),
            LocalDate.parse("2026-08-31"),
            "Asia/Seoul",
            NOW
        ));
        studentRepository.save(ClassroomStudent.enroll(
            studentId, id, 1, "budget-" + id.toString().substring(0, 6), NOW
        ));
        seedIssuance(id, studentId, nonRewardIssuance);
        return id;
    }

    private void seedIssuance(UUID targetClassroom, UUID studentId, long amount) {
        LedgerAccount issuance = ledgerAccountRepository.save(LedgerAccount.systemAccount(
            UUID.randomUUID(), targetClassroom, "MONEY_ISSUANCE", LedgerAccountType.MONEY_ISSUANCE, NOW
        ));
        LedgerAccount wallet = ledgerAccountRepository.save(LedgerAccount.studentWallet(
            UUID.randomUUID(), targetClassroom, studentId, NOW
        ));
        transactionService.execute(LedgerTransactionCommand.original(
            targetClassroom,
            "BASE_ISSUANCE",
            LedgerActorType.TEACHER,
            teacherId,
            null,
            "/tests/base-issuance",
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "test issuance",
            List.of(
                new LedgerTransactionPosting(issuance.getId(), -amount),
                new LedgerTransactionPosting(wallet.getId(), amount)
            ),
            NOW
        ));
    }

    private JsonNode commandResult(UUID key, ObjectNode body) throws Exception {
        return response(resultCommandRequest(classroomId, sessionId, key, body));
    }

    private JsonNode response(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) throws Exception {
        MvcResult result = mockMvc.perform(request)
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
        resultCommandRequest(
            UUID targetClassroom,
            UUID targetSession,
            UUID key,
            ObjectNode body
        ) {
        return post(
            "/classrooms/{classroomId}/quiz-sessions/{sessionId}/result-commands",
            targetClassroom,
            targetSession
        )
            .principal(teacher)
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body.toString());
    }

    private ObjectNode body(
        String command,
        long expectedStateVersion,
        Integer expectedResultVersion,
        Boolean leaderboardPublic
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("command", command);
        body.put("expected_state_version", expectedStateVersion);
        if (expectedResultVersion != null) {
            body.put("expected_result_version", expectedResultVersion);
        }
        if (leaderboardPublic != null) {
            body.put("leaderboard_public", leaderboardPublic);
        }
        return body;
    }

    private int count(String table, String predicate) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE " + predicate + " AND classroom_id = ?",
            Integer.class,
            classroomId
        );
    }

    private long walletBalance(UUID studentId) {
        // 보상을 한 번도 받지 못한 학생은 지갑 계정 자체가 없을 수 있다. 그 잔액은 0으로 본다.
        Long balance = jdbcTemplate.query(
            "SELECT current_balance FROM ledger_accounts WHERE classroom_id = ? "
                + "AND classroom_student_id = ? AND account_type = 'STUDENT_WALLET'",
            resultSet -> resultSet.next() ? resultSet.getLong(1) : null,
            classroomId,
            studentId
        );
        return balance == null ? 0L : balance;
    }

    private static String classroomCode() {
        String value = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return value.substring(0, 8).replace('I', 'J').replace('O', 'P').replace('L', 'M');
    }
}
