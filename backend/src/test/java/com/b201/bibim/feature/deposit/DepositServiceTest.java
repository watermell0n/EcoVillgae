package com.b201.bibim.feature.deposit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.b201.bibim.domain.deposit.DepositEntity;
import com.b201.bibim.domain.deposit.DepositRepository;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.finance.FinancialEntryRepository;
import com.b201.bibim.domain.finance.FinancialTransactionRepository;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.DaySessionEntity;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.deposit.DepositDto.SaveDraftRequest;
import com.b201.bibim.feature.flow.StudentFlowService;
import com.b201.bibim.global.exception.ApiException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepositServiceTest {

    @Mock DepositRepository depositRepository;
    @Mock StudentRepository studentRepository;
    @Mock RoomRepository roomRepository;
    @Mock DaySessionRepository sessionRepository;
    @Mock AccountRepository accountRepository;
    @Mock FinancialTransactionRepository transactionRepository;
    @Mock FinancialEntryRepository entryRepository;
    @Mock StudentFlowService studentFlowService;
    @Mock StudentEntity student;
    @Mock RoomEntity room;
    @Mock DaySessionEntity session;

    @InjectMocks DepositService depositService;

    @Test
    void rejectsDepositThatIsNotInTenBibimUnits() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> depositService.saveDraft(java.util.UUID.randomUUID(), new SaveDraftRequest(25))
        );

        assertEquals("INVALID_DEPOSIT_AMOUNT", exception.getCode());
    }

    @Test
    void requiresIdempotencyKeyBeforeConfirmingDeposit() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> depositService.confirm(java.util.UUID.randomUUID(), " ")
        );

        assertEquals("IDEMPOTENCY_KEY_REQUIRED", exception.getCode());
    }

    @Test
    void returnsEveryConfirmedDepositInHistory() {
        UUID studentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        DepositEntity deposit = DepositEntity.draft(roomId, sessionId, studentId, 40, 500, 2);
        deposit.confirm(UUID.randomUUID());
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(student.getStatus()).thenReturn("ACTIVE");
        when(student.getRoomId()).thenReturn(roomId);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(roomId);
        when(room.getCurrentStage()).thenReturn("DAY1_FREE_ACTIVITY");
        when(sessionRepository.findByRoomIdAndDayNumber(roomId, (short) 1))
                .thenReturn(Optional.of(session));
        when(session.getId()).thenReturn(sessionId);
        when(depositRepository.findAllBySessionIdAndStudentIdAndStatusOrderByConfirmedAtDesc(
                sessionId, studentId, "CONFIRMED"
        )).thenReturn(List.of(deposit));

        var result = depositService.history(studentId);

        assertEquals(1, result.deposits().size());
        assertEquals(40, result.deposits().getFirst().principal());
        assertEquals(42, result.deposits().getFirst().expectedMaturityAmount());
    }
}
