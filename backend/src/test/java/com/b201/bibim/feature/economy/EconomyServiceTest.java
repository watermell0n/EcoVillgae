package com.b201.bibim.feature.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.b201.bibim.domain.deposit.DepositRepository;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.finance.FinancialEntryRepository;
import com.b201.bibim.domain.finance.FinancialTransactionRepository;
import com.b201.bibim.domain.investment.InvestmentOrderRepository;
import com.b201.bibim.domain.room.DaySessionEntity;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.shop.PurchaseRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EconomyServiceTest {

    @Mock StudentRepository studentRepository;
    @Mock RoomRepository roomRepository;
    @Mock DaySessionRepository sessionRepository;
    @Mock AccountRepository accountRepository;
    @Mock DepositRepository depositRepository;
    @Mock InvestmentOrderRepository orderRepository;
    @Mock PurchaseRepository purchaseRepository;
    @Mock FinancialTransactionRepository transactionRepository;
    @Mock FinancialEntryRepository entryRepository;

    @InjectMocks EconomyService economyService;

    @Test
    void allowsEconomyOverviewDuringDayTwoFinalShop() {
        UUID studentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        StudentEntity student = org.mockito.Mockito.mock(StudentEntity.class);
        RoomEntity room = org.mockito.Mockito.mock(RoomEntity.class);
        DaySessionEntity session = org.mockito.Mockito.mock(DaySessionEntity.class);

        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(student.getStatus()).thenReturn("ACTIVE");
        when(student.getRoomId()).thenReturn(roomId);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getCurrentStage()).thenReturn("DAY2_FINAL_SHOP");
        when(room.getId()).thenReturn(roomId);
        when(sessionRepository.findByRoomIdAndDayNumber(roomId, (short) 1))
                .thenReturn(Optional.of(session));
        when(session.getId()).thenReturn(UUID.randomUUID());
        when(accountRepository.findByRoomIdAndStudentIdAndAccountType(
                any(UUID.class), any(UUID.class), anyString()
        )).thenReturn(Optional.empty());

        EconomyDto.OverviewResponse response = economyService.overview(studentId);

        assertEquals(0, response.walletBalance());
        assertEquals(0, response.totalAssets());
    }
}
