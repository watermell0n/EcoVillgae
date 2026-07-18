package com.b201.bibim.feature.investment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.finance.FinancialEntryRepository;
import com.b201.bibim.domain.finance.FinancialTransactionRepository;
import com.b201.bibim.domain.investment.InvestmentAllocationRepository;
import com.b201.bibim.domain.investment.InvestmentAssetEntity;
import com.b201.bibim.domain.investment.InvestmentAssetRepository;
import com.b201.bibim.domain.investment.InvestmentOrderRepository;
import com.b201.bibim.domain.investment.InvestmentScenarioEntity;
import com.b201.bibim.domain.investment.InvestmentScenarioRepository;
import com.b201.bibim.domain.investment.InvestmentScenarioReturnRepository;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.investment.InvestmentDto.AllocationRequest;
import com.b201.bibim.feature.investment.InvestmentDto.SaveDraftRequest;
import com.b201.bibim.feature.flow.StudentFlowService;
import com.b201.bibim.global.exception.ApiException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentServiceTest {

    @Mock InvestmentAssetRepository assetRepository;
    @Mock InvestmentScenarioRepository scenarioRepository;
    @Mock InvestmentScenarioReturnRepository scenarioReturnRepository;
    @Mock InvestmentOrderRepository orderRepository;
    @Mock InvestmentAllocationRepository allocationRepository;
    @Mock StudentRepository studentRepository;
    @Mock RoomRepository roomRepository;
    @Mock DaySessionRepository sessionRepository;
    @Mock AccountRepository accountRepository;
    @Mock FinancialTransactionRepository transactionRepository;
    @Mock FinancialEntryRepository entryRepository;
    @Mock StudentFlowService studentFlowService;
    @Mock StudentEntity student;
    @Mock RoomEntity room;
    @Mock InvestmentAssetEntity asset;
    @Mock InvestmentScenarioEntity scenario;

    @InjectMocks InvestmentService investmentService;

    @Test
    void rejectsDuplicateInvestmentAssets() {
        UUID studentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(student.getStatus()).thenReturn("ACTIVE");
        when(student.getRoomId()).thenReturn(roomId);
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(roomId);
        when(room.getCurrentStage()).thenReturn("DAY1_FREE_ACTIVITY");
        when(studentRepository.findByIdForUpdate(studentId)).thenReturn(Optional.of(student));
        when(assetRepository.findAllByRoomIdOrderByDisplayOrder(roomId)).thenReturn(List.of(asset));
        when(asset.getId()).thenReturn(assetId);
        when(scenarioRepository.findByRoomId(roomId)).thenReturn(Optional.of(scenario));

        ApiException exception = assertThrows(ApiException.class, () -> investmentService.saveDraft(
                studentId,
                new SaveDraftRequest(List.of(
                        new AllocationRequest(assetId, 10),
                        new AllocationRequest(assetId, 20)
                ))
        ));

        assertEquals("INVALID_INVESTMENT_ALLOCATION", exception.getCode());
    }
}
