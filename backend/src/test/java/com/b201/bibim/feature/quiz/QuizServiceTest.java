package com.b201.bibim.feature.quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import com.b201.bibim.domain.quiz.QuizAnswerRepository;
import com.b201.bibim.domain.quiz.QuizAnswerEntity;
import com.b201.bibim.domain.quiz.QuizEntity;
import com.b201.bibim.domain.quiz.QuizQuestionEntity;
import com.b201.bibim.domain.quiz.QuizQuestionRepository;
import com.b201.bibim.domain.quiz.QuizRepository;
import com.b201.bibim.domain.quiz.QuizSubmissionEntity;
import com.b201.bibim.domain.quiz.QuizSubmissionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.global.exception.ApiException;
import com.b201.bibim.feature.monitoring.ActivityEventService;
import com.b201.bibim.feature.flow.StudentFlowService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuizQuestionRepository questionRepository;

    @Mock
    private QuizSubmissionRepository submissionRepository;

    @Mock
    private QuizAnswerRepository answerRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private ActivityEventService activityEventService;

    @Mock
    private StudentFlowService studentFlowService;

    @Mock
    private StudentEntity student;

    @Mock
    private RoomEntity room;

    @Mock
    private QuizEntity quiz;

    @Mock
    private QuizSubmissionEntity submission;

    @InjectMocks
    private QuizService quizService;

    @Test
    void rejectsDuplicateSubmission() {
        UUID studentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID quizId = UUID.randomUUID();
        prepareOpenQuiz(studentId, roomId, quizId);
        when(submissionRepository.findByQuizIdAndStudentId(quizId, studentId))
                .thenReturn(Optional.of(submission));
        when(submission.isSubmitted()).thenReturn(true);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> quizService.submit(studentId, quizId)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("QUIZ_ALREADY_SUBMITTED", exception.getCode());
    }

    @Test
    void rejectsQuizAccessOutsideQuizStage() {
        UUID studentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID quizId = UUID.randomUUID();
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(student.getStatus()).thenReturn("ACTIVE");
        when(student.getRoomId()).thenReturn(roomId);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(roomId);
        when(room.getCurrentDay()).thenReturn(1);
        when(room.getCurrentStage()).thenReturn("DAY1_LOBBY");
        when(quizRepository.findByRoomIdAndDayNumber(roomId, (short) 1))
                .thenReturn(Optional.of(quiz));
        when(quiz.getStatus()).thenReturn("OPEN");
        when(quiz.getDayNumber()).thenReturn(1);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> quizService.currentQuiz(studentId)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("QUIZ_NOT_OPEN", exception.getCode());
    }

    @Test
    void rejectsQuizAccessBeforeTeacherOpensIt() {
        UUID studentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(student.getStatus()).thenReturn("ACTIVE");
        when(student.getRoomId()).thenReturn(roomId);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(roomId);
        when(quizRepository.findByRoomIdAndDayNumber(roomId, (short) 1))
                .thenReturn(Optional.of(quiz));
        when(quiz.getStatus()).thenReturn("READY");

        ApiException exception = assertThrows(
                ApiException.class,
                () -> quizService.currentQuiz(studentId)
        );

        assertEquals("QUIZ_NOT_OPEN", exception.getCode());
    }

    @Test
    void allowsCatchUpStudentToSubmitAfterGlobalQuizClose() {
        UUID studentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID quizId = UUID.randomUUID();
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(student.getStatus()).thenReturn("ACTIVE");
        when(student.getRoomId()).thenReturn(roomId);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(roomId);
        when(room.getCurrentStage()).thenReturn("DAY1_INCOME_SETTLEMENT");
        when(studentFlowService.effectiveStage(studentId, "DAY1_INCOME_SETTLEMENT"))
                .thenReturn("DAY1_QUIZ");
        when(quizRepository.findByRoomIdAndDayNumber(roomId, (short) 1))
                .thenReturn(Optional.of(quiz));
        when(quiz.getId()).thenReturn(quizId);
        when(quiz.getDayNumber()).thenReturn(1);
        when(studentFlowService.canUseStage(studentId, "DAY1_QUIZ")).thenReturn(true);
        when(questionRepository.findAllByQuizIdOrderByOrderNo(quizId)).thenReturn(List.of());
        when(submissionRepository.findByQuizIdAndStudentId(quizId, studentId))
                .thenReturn(Optional.empty());

        var response = quizService.currentQuiz(studentId);

        assertEquals(quizId, response.quizId());
        assertEquals("NOT_STARTED", response.submissionStatus());
    }

    @Test
    void teacherSummaryCountsOnlyActiveStudentsAsEligible() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID quizId = UUID.randomUUID();
        StudentEntity active = org.mockito.Mockito.mock(StudentEntity.class);
        StudentEntity invited = org.mockito.Mockito.mock(StudentEntity.class);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getCurrentDay()).thenReturn(1);
        when(room.getExpectedStudentCount()).thenReturn(2);
        when(quizRepository.findByRoomIdAndDayNumber(roomId, (short) 1))
                .thenReturn(Optional.of(quiz));
        when(quiz.getId()).thenReturn(quizId);
        when(quiz.getStatus()).thenReturn("OPEN");
        when(studentRepository.findAllByRoomIdOrderByParticipantNumber(roomId))
                .thenReturn(List.of(active, invited));
        when(active.getStatus()).thenReturn("ACTIVE");
        when(active.getId()).thenReturn(UUID.randomUUID());
        when(invited.getStatus()).thenReturn("INVITED");

        var summary = quizService.teacherSummary(teacherId, roomId);

        assertEquals(1, summary.eligibleStudentCount());
        assertEquals(1, summary.notSubmittedCount());
        assertEquals(1, summary.students().size());
    }

    @Test
    void returnsDayOneQuestionResultsAfterStudentReachesSettlement() {
        UUID studentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        QuizEntity closedQuiz = QuizEntity.dayOneReady(roomId, 1);
        closedQuiz.open();
        closedQuiz.close();
        QuizQuestionEntity question = QuizQuestionEntity.multipleChoice(
                closedQuiz.getId(),
                1,
                "역할의 일을 하고 받는 돈은 무엇일까요?",
                List.of(
                        new QuizQuestionEntity.QuizOption("A", "역할 소득"),
                        new QuizQuestionEntity.QuizOption("B", "상품 가격")
                ),
                "A",
                "역할을 수행하고 받는 돈을 역할 소득이라고 해요."
        );
        QuizSubmissionEntity submitted = QuizSubmissionEntity.start(closedQuiz.getId(), studentId);
        submitted.submit(1);
        QuizAnswerEntity answer = QuizAnswerEntity.create(
                submitted.getId(), question.getId(), "A", true
        );
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(student.getStatus()).thenReturn("ACTIVE");
        when(student.getRoomId()).thenReturn(roomId);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(roomId);
        when(room.getCurrentStage()).thenReturn("DAY1_INCOME_SETTLEMENT");
        when(studentFlowService.effectiveStage(studentId, "DAY1_INCOME_SETTLEMENT"))
                .thenReturn("DAY1_INCOME_SETTLEMENT");
        when(quizRepository.findByRoomIdAndDayNumber(roomId, (short) 1))
                .thenReturn(Optional.of(closedQuiz));
        when(questionRepository.findAllByQuizIdOrderByOrderNo(closedQuiz.getId()))
                .thenReturn(List.of(question));
        when(submissionRepository.findByQuizIdAndStudentId(closedQuiz.getId(), studentId))
                .thenReturn(Optional.of(submitted));
        when(answerRepository.findAllBySubmissionId(submitted.getId()))
                .thenReturn(List.of(answer));

        var result = quizService.dayOneResult(studentId);

        assertEquals(1, result.correctCount());
        assertEquals("A", result.questions().getFirst().correctAnswer());
        assertEquals("A", result.questions().getFirst().selectedAnswer());
        assertEquals("역할을 수행하고 받는 돈을 역할 소득이라고 해요.",
                result.questions().getFirst().explanation());
    }

    @Test
    void hidesResultsFromCatchUpStudentWhoIsStillTakingTheQuiz() {
        UUID studentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(student.getStatus()).thenReturn("ACTIVE");
        when(student.getRoomId()).thenReturn(roomId);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getCurrentStage()).thenReturn("DAY1_INCOME_SETTLEMENT");
        when(studentFlowService.effectiveStage(studentId, "DAY1_INCOME_SETTLEMENT"))
                .thenReturn("DAY1_QUIZ");

        ApiException exception = assertThrows(
                ApiException.class,
                () -> quizService.dayOneResult(studentId)
        );

        assertEquals("QUIZ_RESULT_NOT_AVAILABLE", exception.getCode());
    }

    private void prepareOpenQuiz(UUID studentId, UUID roomId, UUID quizId) {
        when(studentRepository.findByIdForUpdate(studentId)).thenReturn(Optional.of(student));
        when(student.getStatus()).thenReturn("ACTIVE");
        when(student.getRoomId()).thenReturn(roomId);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(roomId);
        when(room.getCurrentDay()).thenReturn(1);
        when(room.getCurrentStage()).thenReturn("DAY1_QUIZ");
        when(quizRepository.findById(quizId)).thenReturn(Optional.of(quiz));
        when(quiz.getRoomId()).thenReturn(roomId);
        when(quiz.getStatus()).thenReturn("OPEN");
        when(quiz.getDayNumber()).thenReturn(1);
    }
}
