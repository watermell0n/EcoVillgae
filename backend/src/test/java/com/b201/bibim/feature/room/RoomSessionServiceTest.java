package com.b201.bibim.feature.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.fund.FundExpenseRepository;
import com.b201.bibim.domain.quiz.QuizEntity;
import com.b201.bibim.domain.quiz.QuizRepository;
import com.b201.bibim.domain.quiz.QuizSubmissionRepository;
import com.b201.bibim.domain.room.RoomCommandRequestRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.room.RoomStage;
import com.b201.bibim.domain.room.DaySessionEntity;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.settlement.SettlementRunRepository;
import com.b201.bibim.domain.vote.VoteSessionRepository;
import com.b201.bibim.feature.day1.Day1Service;
import com.b201.bibim.feature.flow.StudentFlowService;
import com.b201.bibim.feature.settlement.Day2QuizRewardService;
import com.b201.bibim.feature.room.RoomSessionDto.CommandRequest;
import com.b201.bibim.global.exception.ApiException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class RoomSessionServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomCommandRequestRepository commandRequestRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SettlementRunRepository settlementRunRepository;

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuizSubmissionRepository quizSubmissionRepository;

    @Mock
    private Day1Service day1Service;

    @Mock
    private DaySessionRepository sessionRepository;

    @Mock
    private Day2QuizRewardService day2QuizRewardService;

    @Mock
    private FundExpenseRepository fundExpenseRepository;

    @Mock
    private VoteSessionRepository voteSessionRepository;

    @Mock
    private StudentFlowService studentFlowService;

    @Mock
    private RoomEntity room;

    @Mock
    private QuizEntity quiz;

    @InjectMocks
    private RoomSessionService roomSessionService;

    @Test
    void allowsDirectNavigationToANonAdjacentStageInTheSameDay() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getId()).thenReturn(roomId);
        when(room.getVersion()).thenReturn(3);
        when(room.getCurrentStage()).thenReturn("DAY1_PAYSLIP_GUIDED");
        when(commandRequestRepository.findByRoomIdAndIdempotencyKey(roomId, "request-1"))
                .thenReturn(Optional.empty());

        roomSessionService.command(
                teacherId,
                roomId,
                "request-1",
                new CommandRequest("SET_STAGE", "DAY1_FINANCE_GUIDED", 3)
        );

        verify(studentFlowService).captureStageCompletion(room, "DAY1_PAYSLIP_GUIDED");
        verify(room).changeStage(RoomStage.DAY1_FINANCE_GUIDED);
        verify(studentFlowService).roomStageChanged(room);
    }

    @Test
    void cancelsUnconfirmedFinanceDraftsBeforeLeavingFreeActivity() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getVersion()).thenReturn(4);
        when(room.getCurrentStage()).thenReturn("DAY1_FREE_ACTIVITY");
        when(commandRequestRepository.findByRoomIdAndIdempotencyKey(roomId, "request-drafts"))
                .thenReturn(Optional.empty());

        roomSessionService.command(
                teacherId,
                roomId,
                "request-drafts",
                new CommandRequest("SET_STAGE", "DAY1_SUMMARY", 4)
        );

        verify(day1Service).cancelUnconfirmedDrafts(roomId);
        verify(room).changeStage(RoomStage.DAY1_SUMMARY);
    }

    @Test
    void rejectsSkippingPastIncompleteIncomeSettlement() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getVersion()).thenReturn(4);
        when(room.getCurrentStage()).thenReturn("DAY1_INCOME_SETTLEMENT");
        when(commandRequestRepository.findByRoomIdAndIdempotencyKey(roomId, "skip-settlement"))
                .thenReturn(Optional.empty());
        when(settlementRunRepository.existsByRoomIdAndSettlementTypeAndStatus(
                roomId, "DAY1_INCOME", "COMPLETED"
        )).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () -> roomSessionService.command(
                teacherId,
                roomId,
                "skip-settlement",
                new CommandRequest("SET_STAGE", "DAY1_KNOWLEDGE_GUIDED", 4)
        ));

        assertEquals("STATE_CONFLICT", exception.getCode());
        verify(room, never()).changeStage(RoomStage.DAY1_KNOWLEDGE_GUIDED);
    }

    @Test
    void rejectsStaleExpectedVersionWithStateConflict() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getVersion()).thenReturn(4);

        ApiException exception = assertThrows(ApiException.class, () -> roomSessionService.command(
                teacherId,
                roomId,
                "request-2",
                new CommandRequest("SET_STAGE", "DAY1_PROFILE", 3)
        ));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("STATE_CONFLICT", exception.getCode());
        verify(room, never()).changeStage(RoomStage.DAY1_PROFILE);
    }

    @Test
    void rejectsPayslipStageUntilIncomeSettlementCompletes() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getVersion()).thenReturn(5);
        when(room.getCurrentStage()).thenReturn("DAY1_INCOME_SETTLEMENT");
        when(commandRequestRepository.findByRoomIdAndIdempotencyKey(roomId, "request-3"))
                .thenReturn(Optional.empty());
        when(settlementRunRepository.existsByRoomIdAndSettlementTypeAndStatus(
                roomId,
                "DAY1_INCOME",
                "COMPLETED"
        )).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () -> roomSessionService.command(
                teacherId,
                roomId,
                "request-3",
                new CommandRequest("SET_STAGE", "DAY1_PAYSLIP_GUIDED", 5)
        ));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("STATE_CONFLICT", exception.getCode());
        verify(room, never()).changeStage(RoomStage.DAY1_PAYSLIP_GUIDED);
    }

    @Test
    void rejectsReturningToQuizAfterIncomeSettlementCompletes() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getVersion()).thenReturn(6);
        when(room.getCurrentStage()).thenReturn("DAY1_INCOME_SETTLEMENT");
        when(commandRequestRepository.findByRoomIdAndIdempotencyKey(roomId, "request-4"))
                .thenReturn(Optional.empty());
        when(settlementRunRepository.existsByRoomIdAndSettlementTypeAndStatus(
                roomId,
                "DAY1_INCOME",
                "COMPLETED"
        )).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> roomSessionService.command(
                teacherId,
                roomId,
                "request-4",
                new CommandRequest("SET_STAGE", "DAY1_QUIZ", 6)
        ));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("STATE_CONFLICT", exception.getCode());
        verify(room, never()).changeStage(RoomStage.DAY1_QUIZ);
    }

    @Test
    void opensReadyQuizAndAdvancesRoomControlVersion() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getId()).thenReturn(roomId);
        when(room.getVersion()).thenReturn(7);
        when(room.getCurrentDay()).thenReturn(1);
        when(room.getCurrentStage()).thenReturn("DAY1_QUIZ");
        when(commandRequestRepository.findByRoomIdAndIdempotencyKey(roomId, "quiz-open"))
                .thenReturn(Optional.empty());
        when(quizRepository.findByRoomIdAndDayNumber(roomId, (short) 1))
                .thenReturn(Optional.of(quiz));
        when(quiz.getStatus()).thenReturn("READY");

        roomSessionService.command(
                teacherId,
                roomId,
                "quiz-open",
                new CommandRequest("OPEN_QUIZ", "DAY1_QUIZ", 7)
        );

        verify(quiz).open();
        verify(room).markControlStateChanged();
    }

    @Test
    void rejectsClosingQuizWhileActiveStudentHasNotSubmitted() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        StudentEntity activeStudent = org.mockito.Mockito.mock(StudentEntity.class);
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getId()).thenReturn(roomId);
        when(room.getVersion()).thenReturn(7);
        when(room.getCurrentDay()).thenReturn(1);
        when(room.getCurrentStage()).thenReturn("DAY1_QUIZ");
        when(commandRequestRepository.findByRoomIdAndIdempotencyKey(roomId, "quiz-close"))
                .thenReturn(Optional.empty());
        when(quizRepository.findByRoomIdAndDayNumber(roomId, (short) 1))
                .thenReturn(Optional.of(quiz));
        when(quiz.getId()).thenReturn(UUID.randomUUID());
        when(quiz.getStatus()).thenReturn("OPEN");
        when(studentRepository.findAllByRoomIdAndStatusOrderByParticipantNumber(roomId, "ACTIVE"))
                .thenReturn(List.of(activeStudent));

        ApiException exception = assertThrows(ApiException.class, () -> roomSessionService.command(
                teacherId,
                roomId,
                "quiz-close",
                new CommandRequest("CLOSE_QUIZ", "DAY1_QUIZ", 7)
        ));

        assertEquals("QUIZ_INCOMPLETE", exception.getCode());
        verify(quiz, never()).close();
    }

    @Test
    void forceClosesQuizEvenWhenActiveStudentHasNotSubmitted() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getId()).thenReturn(roomId);
        when(room.getVersion()).thenReturn(7);
        when(room.getCurrentDay()).thenReturn(1);
        when(room.getCurrentStage()).thenReturn("DAY1_QUIZ");
        when(commandRequestRepository.findByRoomIdAndIdempotencyKey(roomId, "quiz-force-close"))
                .thenReturn(Optional.empty());
        when(quizRepository.findByRoomIdAndDayNumber(roomId, (short) 1))
                .thenReturn(Optional.of(quiz));
        when(quiz.getStatus()).thenReturn("OPEN");
        when(quiz.getDayNumber()).thenReturn(1);

        roomSessionService.command(
                teacherId,
                roomId,
                "quiz-force-close",
                new CommandRequest("FORCE_CLOSE_QUIZ", "DAY1_QUIZ", 7)
        );

        verify(quiz).close();
        verify(studentFlowService).markQuizClosed(room, 1);
    }

    @Test
    void rejectsLeavingQuizUntilTeacherClosesIt() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getId()).thenReturn(roomId);
        when(room.getVersion()).thenReturn(8);
        when(room.getCurrentDay()).thenReturn(1);
        when(room.getCurrentStage()).thenReturn("DAY1_QUIZ");
        when(commandRequestRepository.findByRoomIdAndIdempotencyKey(roomId, "quiz-next"))
                .thenReturn(Optional.empty());
        when(quizRepository.findByRoomIdAndDayNumber(roomId, (short) 1))
                .thenReturn(Optional.of(quiz));
        when(quiz.getStatus()).thenReturn("OPEN");

        ApiException exception = assertThrows(ApiException.class, () -> roomSessionService.command(
                teacherId,
                roomId,
                "quiz-next",
                new CommandRequest("SET_STAGE", "DAY1_INCOME_SETTLEMENT", 8)
        ));

        assertEquals("STATE_CONFLICT", exception.getCode());
        verify(room, never()).changeStage(RoomStage.DAY1_INCOME_SETTLEMENT);
    }

    @Test
    void rejectsSkippingDayOneQuizResultAfterQuizClose() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getId()).thenReturn(roomId);
        when(room.getVersion()).thenReturn(8);
        when(room.getCurrentDay()).thenReturn(1);
        when(room.getCurrentStage()).thenReturn("DAY1_QUIZ");
        when(commandRequestRepository.findByRoomIdAndIdempotencyKey(roomId, "skip-quiz-result"))
                .thenReturn(Optional.empty());
        when(quizRepository.findByRoomIdAndDayNumber(roomId, (short) 1))
                .thenReturn(Optional.of(quiz));
        when(quiz.getStatus()).thenReturn("CLOSED");

        ApiException exception = assertThrows(ApiException.class, () -> roomSessionService.command(
                teacherId,
                roomId,
                "skip-quiz-result",
                new CommandRequest("SET_STAGE", "DAY1_INCOME_SETTLEMENT", 8)
        ));

        assertEquals("STATE_CONFLICT", exception.getCode());
        verify(room, never()).changeStage(RoomStage.DAY1_INCOME_SETTLEMENT);
    }

    @Test
    void rejectsSkippingDayTwoQuizResultAfterQuizClose() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getId()).thenReturn(roomId);
        when(room.getVersion()).thenReturn(12);
        when(room.getCurrentDay()).thenReturn(2);
        when(room.getCurrentStage()).thenReturn("DAY2_QUIZ");
        when(commandRequestRepository.findByRoomIdAndIdempotencyKey(roomId, "skip-day2-quiz-result"))
                .thenReturn(Optional.empty());
        when(quizRepository.findByRoomIdAndDayNumber(roomId, (short) 2))
                .thenReturn(Optional.of(quiz));
        when(quiz.getStatus()).thenReturn("CLOSED");

        ApiException exception = assertThrows(ApiException.class, () -> roomSessionService.command(
                teacherId,
                roomId,
                "skip-day2-quiz-result",
                new CommandRequest("SET_STAGE", "DAY2_QUIZ_SETTLEMENT", 12)
        ));

        assertEquals("STATE_CONFLICT", exception.getCode());
        verify(room, never()).changeStage(RoomStage.DAY2_QUIZ_SETTLEMENT);
    }

    @Test
    void completesRoomOnlyThroughExplicitCommand() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        DaySessionEntity dayTwo = org.mockito.Mockito.mock(DaySessionEntity.class);
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getVersion()).thenReturn(9);
        when(room.getCurrentStage()).thenReturn("DAY2_SURVEY");
        when(room.getStatus()).thenReturn("DAY2_ACTIVE");
        when(sessionRepository.findByRoomIdAndDayNumber(roomId, (short) 2)).thenReturn(Optional.of(dayTwo));

        roomSessionService.command(
                teacherId,
                roomId,
                "complete-room-request",
                new CommandRequest("COMPLETE_ROOM", "DAY2_COMPLETED", 9)
        );

        verify(dayTwo).closeDayTwo();
        verify(room).complete();
    }

    @Test
    void blocksNewCommandsAfterRoomCompleted() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getVersion()).thenReturn(10);
        when(room.getStatus()).thenReturn("COMPLETED");

        ApiException exception = assertThrows(ApiException.class, () -> roomSessionService.command(
                teacherId,
                roomId,
                "after-complete-request",
                new CommandRequest("SET_STAGE", "DAY2_SURVEY", 10)
        ));

        assertEquals("STATE_CONFLICT", exception.getCode());
        verify(room, never()).changeStage(any());
    }

    @Test
    void closesDayOneOnlyThroughExplicitCommand() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getId()).thenReturn(roomId);
        when(room.getVersion()).thenReturn(9);
        when(room.getCurrentDay()).thenReturn(1);
        when(room.getCurrentStage()).thenReturn("DAY1_SUMMARY");
        when(commandRequestRepository.findByRoomIdAndIdempotencyKey(roomId, "day1-close"))
                .thenReturn(Optional.empty());

        roomSessionService.command(
                teacherId,
                roomId,
                "day1-close",
                new CommandRequest("CLOSE_DAY1", "DAY1_SUMMARY", 9)
        );

        verify(day1Service).closeDayOne(teacherId, room);
    }

    @Test
    void rejectsUsingSetStageToBypassDayOneClose() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getVersion()).thenReturn(10);
        when(commandRequestRepository.findByRoomIdAndIdempotencyKey(roomId, "day1-bypass"))
                .thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> roomSessionService.command(
                teacherId,
                roomId,
                "day1-bypass",
                new CommandRequest("SET_STAGE", "DAY1_CLOSED", 10)
        ));

        assertEquals("STATE_CONFLICT", exception.getCode());
        verify(day1Service, never()).closeDayOne(teacherId, room);
    }

    @Test
    void startsDayTwoWithAnExplicitCommandAndCreatesSession() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getId()).thenReturn(roomId);
        when(room.getVersion()).thenReturn(11);
        when(room.getCurrentDay()).thenReturn(1);
        when(room.getCurrentStage()).thenReturn("DAY1_CLOSED");
        when(commandRequestRepository.findByRoomIdAndIdempotencyKey(roomId, "day2-start"))
                .thenReturn(Optional.empty());
        when(sessionRepository.findByRoomIdAndDayNumber(roomId, (short) 2))
                .thenReturn(Optional.empty());

        roomSessionService.command(
                teacherId,
                roomId,
                "day2-start",
                new CommandRequest("START_DAY2", "DAY2_LOGIN", 11)
        );

        verify(sessionRepository).save(any(DaySessionEntity.class));
        verify(day2QuizRewardService).ensureQuiz(roomId);
        verify(room).startDayTwo();
    }

    @Test
    void rejectsResultStageUntilDayTwoFinanceSettlementCompletes() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getVersion()).thenReturn(12);
        when(room.getCurrentStage()).thenReturn("DAY2_FINANCE_SETTLEMENT");
        when(commandRequestRepository.findByRoomIdAndIdempotencyKey(roomId, "day2-result"))
                .thenReturn(Optional.empty());
        when(settlementRunRepository.existsByRoomIdAndSettlementTypeAndStatus(
                roomId, "DAY2_FINANCE", "COMPLETED"
        )).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () -> roomSessionService.command(
                teacherId,
                roomId,
                "day2-result",
                new CommandRequest("SET_STAGE", "DAY2_RESULT_GUIDED", 12)
        ));

        assertEquals("STATE_CONFLICT", exception.getCode());
        verify(room, never()).changeStage(RoomStage.DAY2_RESULT_GUIDED);
    }

    @Test
    void rejectsInflationLessonUntilDayTwoQuizRewardCompletes() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getVersion()).thenReturn(13);
        when(room.getCurrentStage()).thenReturn("DAY2_QUIZ_SETTLEMENT");
        when(commandRequestRepository.findByRoomIdAndIdempotencyKey(roomId, "day2-reward-next"))
                .thenReturn(Optional.empty());
        when(settlementRunRepository.existsByRoomIdAndSettlementTypeAndStatus(
                roomId, "DAY2_QUIZ_REWARD", "COMPLETED"
        )).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () -> roomSessionService.command(
                teacherId,
                roomId,
                "day2-reward-next",
                new CommandRequest("SET_STAGE", "DAY2_TAX_INFLATION_GUIDED", 13)
        ));

        assertEquals("STATE_CONFLICT", exception.getCode());
        verify(room, never()).changeStage(RoomStage.DAY2_TAX_INFLATION_GUIDED);
    }

    @Test
    void rejectsFinalShopUntilInflationIsApplied() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getVersion()).thenReturn(14);
        when(room.getCurrentStage()).thenReturn("DAY2_INFLATION_APPLY");
        when(commandRequestRepository.findByRoomIdAndIdempotencyKey(roomId, "day2-shop-next"))
                .thenReturn(Optional.empty());
        when(room.getInflationAppliedAt()).thenReturn(null);

        ApiException exception = assertThrows(ApiException.class, () -> roomSessionService.command(
                teacherId,
                roomId,
                "day2-shop-next",
                new CommandRequest("SET_STAGE", "DAY2_FINAL_SHOP", 14)
        ));

        assertEquals("STATE_CONFLICT", exception.getCode());
        verify(room, never()).changeStage(RoomStage.DAY2_FINAL_SHOP);
    }

    @Test
    void rejectsSkippingPastFundVotePreparation() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getVersion()).thenReturn(15);
        when(room.getCurrentStage()).thenReturn("DAY2_FINAL_SHOP");
        when(commandRequestRepository.findByRoomIdAndIdempotencyKey(roomId, "skip-fund-vote"))
                .thenReturn(Optional.empty());
        when(voteSessionRepository.findByRoomId(roomId)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> roomSessionService.command(
                teacherId,
                roomId,
                "skip-fund-vote",
                new CommandRequest("SET_STAGE", "DAY2_FINAL_RESULT", 15)
        ));

        assertEquals("STATE_CONFLICT", exception.getCode());
        verify(room, never()).changeStage(RoomStage.DAY2_FINAL_RESULT);
    }
}
