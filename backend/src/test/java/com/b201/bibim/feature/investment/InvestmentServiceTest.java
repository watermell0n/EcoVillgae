package com.b201.bibim.feature.investment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.finance.FinancialEntryRepository;
import com.b201.bibim.domain.finance.FinancialTransactionRepository;
import com.b201.bibim.domain.investment.InvestmentAllocationRepository;
import com.b201.bibim.domain.investment.InvestmentAllocationEntity;
import com.b201.bibim.domain.investment.InvestmentAssetEntity;
import com.b201.bibim.domain.investment.InvestmentAssetRepository;
import com.b201.bibim.domain.investment.InvestmentOrderRepository;
import com.b201.bibim.domain.investment.InvestmentOrderEntity;
import com.b201.bibim.domain.investment.InvestmentScenarioEntity;
import com.b201.bibim.domain.investment.InvestmentScenarioRepository;
import com.b201.bibim.domain.investment.InvestmentScenarioReturnEntity;
import com.b201.bibim.domain.investment.InvestmentScenarioReturnRepository;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.DaySessionEntity;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.room.RoomRulesEntity;
import com.b201.bibim.domain.room.RoomRulesRepository;
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
    @Mock RoomRulesRepository rulesRepository;
    @Mock DaySessionRepository sessionRepository;
    @Mock AccountRepository accountRepository;
    @Mock FinancialTransactionRepository transactionRepository;
    @Mock FinancialEntryRepository entryRepository;
    @Mock StudentFlowService studentFlowService;
    @Mock StudentEntity student;
    @Mock RoomEntity room;
    @Mock InvestmentAssetEntity asset;
    @Mock InvestmentScenarioEntity scenario;
    @Mock DaySessionEntity session;

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
        when(asset.getCode()).thenReturn("SAFE_BOND");
        when(scenarioRepository.findByRoomId(roomId)).thenReturn(Optional.of(scenario));
        when(rulesRepository.findByRoomId(roomId)).thenReturn(Optional.of(RoomRulesEntity.defaults(roomId)));

        ApiException exception = assertThrows(ApiException.class, () -> investmentService.saveDraft(
                studentId,
                new SaveDraftRequest(List.of(
                        new AllocationRequest(assetId, 10),
                        new AllocationRequest(assetId, 20)
                ))
        ));

        assertEquals("INVALID_INVESTMENT_ALLOCATION", exception.getCode());
    }

    @Test
    void returnsConfirmedInvestmentsWithTheirAllocations() {
        UUID studentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        InvestmentOrderEntity order = InvestmentOrderEntity.draft(roomId, sessionId, studentId, 30);
        order.confirm(UUID.randomUUID(), UUID.randomUUID());
        InvestmentAllocationEntity allocation = InvestmentAllocationEntity.create(
                order.getId(), assetId, 30
        );
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(student.getStatus()).thenReturn("ACTIVE");
        when(student.getRoomId()).thenReturn(roomId);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(roomId);
        when(room.getCurrentStage()).thenReturn("DAY1_FREE_ACTIVITY");
        when(sessionRepository.findByRoomIdAndDayNumber(roomId, (short) 1))
                .thenReturn(Optional.of(session));
        when(session.getId()).thenReturn(sessionId);
        when(assetRepository.findAllByRoomIdOrderByDisplayOrder(roomId)).thenReturn(List.of(asset));
        when(asset.getId()).thenReturn(assetId);
        when(asset.getName()).thenReturn("튼튼 채권");
        when(asset.getRiskLevel()).thenReturn("LOW");
        when(orderRepository.findAllBySessionIdAndStudentIdAndStatusOrderByConfirmedAtDesc(
                sessionId, studentId, "CONFIRMED"
        )).thenReturn(List.of(order));
        when(allocationRepository.findAllByOrderId(order.getId())).thenReturn(List.of(allocation));

        var result = investmentService.history(studentId);

        assertEquals(1, result.investments().size());
        assertEquals(30, result.investments().getFirst().totalAmount());
        assertEquals("튼튼 채권", result.investments().getFirst().allocations().getFirst().assetName());
    }

    @Test
    void updatesConfiguredInvestmentResultBeforeAnyStudentConfirms() {
        UUID roomId = UUID.randomUUID();
        InvestmentAssetEntity configuredAsset = InvestmentAssetEntity.create(
                roomId, "SAFE_BOND", "튼튼 채권", "설명", "회사 정보",
                "LOW", -1_500, 2_500, 1
        );
        InvestmentScenarioEntity configuredScenario = InvestmentScenarioEntity.ready(roomId);
        InvestmentScenarioReturnEntity configuredReturn = InvestmentScenarioReturnEntity.create(
                configuredScenario.getId(), configuredAsset.getId(), 200
        );
        when(assetRepository.findAllByRoomIdOrderByDisplayOrder(roomId))
                .thenReturn(List.of(configuredAsset));
        when(scenarioRepository.findByRoomId(roomId)).thenReturn(Optional.of(configuredScenario));
        when(scenarioRepository.findByRoomIdForUpdate(roomId)).thenReturn(Optional.of(configuredScenario));
        when(scenarioReturnRepository.findAllByScenarioId(configuredScenario.getId()))
                .thenReturn(List.of(configuredReturn));

        investmentService.updateInvestmentResults(roomId, Map.of(configuredAsset.getId(), 1_250));

        assertEquals(1_250, configuredReturn.getReturnBps());
    }

    @Test
    void rejectsInvestmentResultChangeAfterAStudentConfirms() {
        UUID roomId = UUID.randomUUID();
        InvestmentAssetEntity configuredAsset = InvestmentAssetEntity.create(
                roomId, "SAFE_BOND", "튼튼 채권", "설명", "회사 정보",
                "LOW", -1_500, 2_500, 1
        );
        InvestmentScenarioEntity configuredScenario = InvestmentScenarioEntity.ready(roomId);
        configuredScenario.lock();
        InvestmentScenarioReturnEntity configuredReturn = InvestmentScenarioReturnEntity.create(
                configuredScenario.getId(), configuredAsset.getId(), 200
        );
        when(assetRepository.findAllByRoomIdOrderByDisplayOrder(roomId))
                .thenReturn(List.of(configuredAsset));
        when(scenarioRepository.findByRoomId(roomId)).thenReturn(Optional.of(configuredScenario));
        when(scenarioRepository.findByRoomIdForUpdate(roomId)).thenReturn(Optional.of(configuredScenario));
        when(scenarioReturnRepository.findAllByScenarioId(configuredScenario.getId()))
                .thenReturn(List.of(configuredReturn));

        ApiException exception = assertThrows(ApiException.class, () ->
                investmentService.updateInvestmentResults(roomId, Map.of(configuredAsset.getId(), 700))
        );

        assertEquals("INVESTMENT_RESULTS_LOCKED", exception.getCode());
        assertEquals(200, configuredReturn.getReturnBps());
    }
}
