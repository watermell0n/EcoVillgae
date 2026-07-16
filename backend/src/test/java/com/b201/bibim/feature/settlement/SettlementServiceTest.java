package com.b201.bibim.feature.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.finance.FinancialEntryRepository;
import com.b201.bibim.domain.finance.FinancialTransactionEntity;
import com.b201.bibim.domain.finance.FinancialTransactionRepository;
import com.b201.bibim.domain.quiz.QuizRepository;
import com.b201.bibim.domain.quiz.QuizSubmissionRepository;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.room.RoomRulesEntity;
import com.b201.bibim.domain.room.RoomRulesRepository;
import com.b201.bibim.domain.settlement.SettlementItemEntity;
import com.b201.bibim.domain.settlement.SettlementItemRepository;
import com.b201.bibim.domain.settlement.SettlementRunEntity;
import com.b201.bibim.domain.settlement.SettlementRunRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.settlement.SettlementDto.Day1IncomeSettlementResponse;
import com.b201.bibim.global.exception.ApiException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private RoomRepository roomRepository;
    @Mock
    private RoomRulesRepository roomRulesRepository;
    @Mock
    private DaySessionRepository sessionRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private QuizRepository quizRepository;
    @Mock
    private QuizSubmissionRepository submissionRepository;
    @Mock
    private SettlementRunRepository settlementRunRepository;
    @Mock
    private SettlementItemRepository settlementItemRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private FinancialTransactionRepository transactionRepository;
    @Mock
    private FinancialEntryRepository entryRepository;
    @Mock
    private Day1IncomeCalculator calculator;
    @Mock
    private RoomEntity room;
    @Mock
    private RoomRulesEntity rules;
    @Mock
    private SettlementRunEntity run;
    @Mock
    private SettlementItemEntity item;
    @Mock
    private StudentEntity student;

    @InjectMocks
    private SettlementService settlementService;

    @Test
    void rejectsPreviewOutsideIncomeSettlementStage() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getCurrentStage()).thenReturn("DAY1_QUIZ");

        ApiException exception = assertThrows(
                ApiException.class,
                () -> settlementService.previewDay1Income(teacherId, roomId)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("SETTLEMENT_NOT_OPEN", exception.getCode());
    }

    @Test
    void returnsCompletedSettlementWithoutPayingTwice() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Instant completedAt = Instant.now();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getCurrentStage()).thenReturn("DAY1_INCOME_SETTLEMENT");
        when(room.getId()).thenReturn(roomId);
        when(settlementRunRepository.findByRoomIdAndSettlementType(roomId, "DAY1_INCOME"))
                .thenReturn(Optional.of(run));
        when(run.getStatus()).thenReturn("COMPLETED");
        when(run.getId()).thenReturn(runId);
        when(run.getCompletedAt()).thenReturn(completedAt);
        when(roomRulesRepository.findByRoomId(roomId)).thenReturn(Optional.of(rules));
        when(rules.getIncomeTaxBps()).thenReturn(1_000);
        when(studentRepository.findAllByRoomIdOrderByParticipantNumber(roomId))
                .thenReturn(List.of(student));
        when(student.getId()).thenReturn(studentId);
        when(student.getParticipantNumber()).thenReturn(1);
        when(student.getNickname()).thenReturn("비빔이");
        when(settlementItemRepository.findAllBySettlementRunId(runId))
                .thenReturn(List.of(item));
        when(item.getStudentId()).thenReturn(studentId);
        when(item.isQuizSubmitted()).thenReturn(true);
        when(item.getCorrectCount()).thenReturn(2);
        when(item.getRoleIncome()).thenReturn(100L);
        when(item.getParticipationReward()).thenReturn(10L);
        when(item.getCorrectReward()).thenReturn(20L);
        when(item.getGrossIncome()).thenReturn(130L);
        when(item.getIncomeTax()).thenReturn(13L);
        when(item.getNetIncome()).thenReturn(117L);
        when(accountRepository.findByRoomIdAndStudentIdIsNullAndAccountType(
                roomId,
                "CLASS_FUND"
        )).thenReturn(Optional.empty());

        Day1IncomeSettlementResponse response = settlementService.settleDay1Income(
                teacherId,
                roomId,
                "retry-key"
        );

        assertEquals(true, response.replayed());
        assertEquals(117, response.settlement().totalNetIncome());
        verify(transactionRepository, never()).save(
                org.mockito.ArgumentMatchers.any(FinancialTransactionEntity.class)
        );
    }
}
