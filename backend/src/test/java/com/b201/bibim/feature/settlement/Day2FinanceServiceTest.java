package com.b201.bibim.feature.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.b201.bibim.domain.deposit.DepositRepository;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.finance.FinancialEntryRepository;
import com.b201.bibim.domain.finance.FinancialTransactionRepository;
import com.b201.bibim.domain.investment.InvestmentAllocationRepository;
import com.b201.bibim.domain.investment.InvestmentAssetRepository;
import com.b201.bibim.domain.investment.InvestmentOrderRepository;
import com.b201.bibim.domain.investment.InvestmentScenarioRepository;
import com.b201.bibim.domain.investment.InvestmentScenarioReturnRepository;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.room.RoomRulesRepository;
import com.b201.bibim.domain.settlement.FinancialSettlementAllocationRepository;
import com.b201.bibim.domain.settlement.FinancialSettlementItemRepository;
import com.b201.bibim.domain.settlement.SettlementRunRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentActivityProgressRepository;
import com.b201.bibim.domain.student.StudentFlowStateRepository;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.settlement.Day2FinanceDto.StudentFinanceResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Day2FinanceServiceTest {

    @Mock RoomRepository roomRepository;
    @Mock RoomRulesRepository rulesRepository;
    @Mock DaySessionRepository sessionRepository;
    @Mock StudentRepository studentRepository;
    @Mock DepositRepository depositRepository;
    @Mock InvestmentOrderRepository orderRepository;
    @Mock InvestmentAllocationRepository investmentAllocationRepository;
    @Mock InvestmentAssetRepository assetRepository;
    @Mock InvestmentScenarioRepository scenarioRepository;
    @Mock InvestmentScenarioReturnRepository scenarioReturnRepository;
    @Mock SettlementRunRepository runRepository;
    @Mock FinancialSettlementItemRepository itemRepository;
    @Mock FinancialSettlementAllocationRepository resultAllocationRepository;
    @Mock AccountRepository accountRepository;
    @Mock FinancialTransactionRepository transactionRepository;
    @Mock FinancialEntryRepository entryRepository;
    @Mock Day2FinanceCalculator calculator;
    @Mock StudentFlowStateRepository flowStateRepository;
    @Mock StudentActivityProgressRepository progressRepository;

    @InjectMocks Day2FinanceService day2FinanceService;

    @Test
    void hidesCalculatedAmountsUntilTeacherCompletesSettlement() {
        UUID studentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        StudentEntity student = org.mockito.Mockito.mock(StudentEntity.class);
        RoomEntity room = org.mockito.Mockito.mock(RoomEntity.class);

        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(student.getStatus()).thenReturn("ACTIVE");
        when(student.getId()).thenReturn(studentId);
        when(student.getRoomId()).thenReturn(roomId);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(roomId);
        when(room.getCurrentDay()).thenReturn(2);
        when(runRepository.findByRoomIdAndSettlementType(roomId, "DAY2_FINANCE"))
                .thenReturn(Optional.empty());

        StudentFinanceResponse response = day2FinanceService.studentResult(studentId);

        assertEquals("NOT_STARTED", response.settlementStatus());
        assertFalse(response.resultAvailable());
        assertEquals(0, response.walletBefore());
        assertEquals(0, response.walletAfter());
        assertTrue(response.allocations().isEmpty());
        verifyNoInteractions(scenarioRepository, scenarioReturnRepository, calculator);
    }
}
