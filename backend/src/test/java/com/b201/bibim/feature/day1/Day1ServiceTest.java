package com.b201.bibim.feature.day1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.b201.bibim.domain.deposit.DepositEntity;
import com.b201.bibim.domain.deposit.DepositRepository;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.finance.FinancialTransactionRepository;
import com.b201.bibim.domain.investment.InvestmentAllocationRepository;
import com.b201.bibim.domain.investment.InvestmentAssetRepository;
import com.b201.bibim.domain.investment.InvestmentOrderEntity;
import com.b201.bibim.domain.investment.InvestmentOrderRepository;
import com.b201.bibim.domain.room.DaySessionEntity;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.settlement.SettlementRunRepository;
import com.b201.bibim.domain.shop.ProductRepository;
import com.b201.bibim.domain.shop.PurchaseRepository;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.global.exception.ApiException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Day1ServiceTest {

    @Mock RoomRepository roomRepository;
    @Mock StudentRepository studentRepository;
    @Mock DaySessionRepository sessionRepository;
    @Mock SettlementRunRepository settlementRunRepository;
    @Mock AccountRepository accountRepository;
    @Mock DepositRepository depositRepository;
    @Mock InvestmentOrderRepository orderRepository;
    @Mock InvestmentAllocationRepository allocationRepository;
    @Mock InvestmentAssetRepository assetRepository;
    @Mock PurchaseRepository purchaseRepository;
    @Mock ProductRepository productRepository;
    @Mock FinancialTransactionRepository transactionRepository;
    @Mock RoomEntity room;
    @Mock DaySessionEntity session;
    @Mock DepositEntity deposit;
    @Mock InvestmentOrderEntity order;

    @InjectMocks Day1Service day1Service;

    private UUID teacherId;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        teacherId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getId()).thenReturn(roomId);
        when(room.getCurrentStage()).thenReturn("DAY1_SUMMARY");
        when(settlementRunRepository.existsByRoomIdAndSettlementTypeAndStatus(
                roomId, "DAY1_INCOME", "COMPLETED"
        )).thenReturn(true);
    }

    @Test
    void cancelsDraftsAndClosesTheRoomAndSession() {
        when(depositRepository.countByRoomIdAndStatus(roomId, "DRAFT")).thenReturn(1L);
        when(orderRepository.countByRoomIdAndStatus(roomId, "DRAFT")).thenReturn(1L);
        when(transactionRepository.existsByRoomIdAndStatus(roomId, "PENDING")).thenReturn(false);
        when(depositRepository.findAllByRoomId(roomId)).thenReturn(List.of(deposit));
        when(deposit.getStatus()).thenReturn("DRAFT");
        when(orderRepository.findAllByRoomId(roomId)).thenReturn(List.of(order));
        when(order.getStatus()).thenReturn("DRAFT");
        when(sessionRepository.findByRoomIdAndDayNumber(roomId, (short) 1))
                .thenReturn(Optional.of(session));

        day1Service.closeDayOne(teacherId, room);

        verify(deposit).cancelDraft();
        verify(order).cancelDraft();
        verify(session).closeDayOne();
        verify(room).closeDayOne();
    }

    @Test
    void rejectsCloseWhileFinancialTransactionIsPending() {
        when(transactionRepository.existsByRoomIdAndStatus(roomId, "PENDING")).thenReturn(true);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> day1Service.closeDayOne(teacherId, room)
        );

        assertEquals("DAY1_CLOSE_VALIDATION_FAILED", exception.getCode());
        verify(room, never()).closeDayOne();
    }
}
