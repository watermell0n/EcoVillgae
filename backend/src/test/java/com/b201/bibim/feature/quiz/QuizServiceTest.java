package com.b201.bibim.feature.quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.b201.bibim.domain.quiz.QuizAnswerRepository;
import com.b201.bibim.domain.quiz.QuizEntity;
import com.b201.bibim.domain.quiz.QuizQuestionRepository;
import com.b201.bibim.domain.quiz.QuizRepository;
import com.b201.bibim.domain.quiz.QuizSubmissionEntity;
import com.b201.bibim.domain.quiz.QuizSubmissionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.global.exception.ApiException;

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
        when(room.getCurrentDay()).thenReturn(1);
        when(quizRepository.findByRoomIdAndDayNumber(roomId, (short) 1))
                .thenReturn(Optional.of(quiz));
        when(quiz.getStatus()).thenReturn("READY");

        ApiException exception = assertThrows(
                ApiException.class,
                () -> quizService.currentQuiz(studentId)
        );

        assertEquals("QUIZ_NOT_OPEN", exception.getCode());
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
