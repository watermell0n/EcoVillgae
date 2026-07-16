package com.b201.bibim.feature.deposit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.b201.bibim.domain.deposit.DepositRepository;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.finance.FinancialEntryRepository;
import com.b201.bibim.domain.finance.FinancialTransactionRepository;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.deposit.DepositDto.SaveDraftRequest;
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
}
