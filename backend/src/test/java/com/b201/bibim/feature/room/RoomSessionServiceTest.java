package com.b201.bibim.feature.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.util.Optional;
import java.util.UUID;

import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.quiz.QuizEntity;
import com.b201.bibim.domain.quiz.QuizRepository;
import com.b201.bibim.domain.room.RoomCommandRequestRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.room.RoomStage;
import com.b201.bibim.domain.room.DaySessionEntity;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.domain.settlement.SettlementRunRepository;
import com.b201.bibim.feature.day1.Day1Service;
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
    private Day1Service day1Service;

    @Mock
    private DaySessionRepository sessionRepository;

    @Mock
    private Day2QuizRewardService day2QuizRewardService;

    @Mock
    private RoomEntity room;

    @Mock
    private QuizEntity quiz;

    @InjectMocks
    private RoomSessionService roomSessionService;

    @Test
    void rejectsNonAdjacentStageWithStateConflict() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getVersion()).thenReturn(3);
        when(room.getCurrentStage()).thenReturn("DAY1_LOBBY");

        ApiException exception = assertThrows(ApiException.class, () -> roomSessionService.command(
                teacherId,
                roomId,
                "request-1",
                new CommandRequest("SET_STAGE", "DAY1_PAYSLIP_GUIDED", 3)
        ));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("STATE_CONFLICT", exception.getCode());
        verify(room, never()).changeStage(RoomStage.DAY1_PAYSLIP_GUIDED);
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
}
