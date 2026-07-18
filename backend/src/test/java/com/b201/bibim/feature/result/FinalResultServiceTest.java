package com.b201.bibim.feature.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.finance.FinancialTransactionEntity;
import com.b201.bibim.domain.finance.FinancialTransactionRepository;
import com.b201.bibim.domain.fund.FundExpenseRepository;
import com.b201.bibim.domain.room.DaySessionEntity;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.settlement.FinancialSettlementItemRepository;
import com.b201.bibim.domain.settlement.SettlementItemRepository;
import com.b201.bibim.domain.settlement.SettlementRunRepository;
import com.b201.bibim.domain.shop.ProductEntity;
import com.b201.bibim.domain.shop.ProductRepository;
import com.b201.bibim.domain.shop.PurchaseEntity;
import com.b201.bibim.domain.shop.PurchaseRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.domain.vote.VoteOptionRepository;
import com.b201.bibim.domain.vote.VoteRepository;
import com.b201.bibim.domain.vote.VoteSessionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinalResultServiceTest {

    @Mock RoomRepository roomRepository;
    @Mock DaySessionRepository sessionRepository;
    @Mock StudentRepository studentRepository;
    @Mock AccountRepository accountRepository;
    @Mock FinancialTransactionRepository transactionRepository;
    @Mock SettlementRunRepository runRepository;
    @Mock SettlementItemRepository settlementItemRepository;
    @Mock FinancialSettlementItemRepository financialItemRepository;
    @Mock PurchaseRepository purchaseRepository;
    @Mock ProductRepository productRepository;
    @Mock FundExpenseRepository expenseRepository;
    @Mock VoteSessionRepository voteSessionRepository;
    @Mock VoteOptionRepository voteOptionRepository;
    @Mock VoteRepository voteRepository;

    FinalResultService service;

    @BeforeEach
    void setUp() {
        service = new FinalResultService(
                roomRepository,
                sessionRepository,
                studentRepository,
                accountRepository,
                transactionRepository,
                runRepository,
                settlementItemRepository,
                financialItemRepository,
                purchaseRepository,
                productRepository,
                expenseRepository,
                voteSessionRepository,
                voteOptionRepository,
                voteRepository,
                "https://example.com/survey"
        );
    }

    @Test
    void combinesRepeatedStudentPurchasesOfTheSameProduct() {
        UUID roomId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        RoomEntity room = mock(RoomEntity.class);
        StudentEntity student = mock(StudentEntity.class);
        ProductEntity product = mock(ProductEntity.class);
        PurchaseEntity first = purchase(studentId, productId, UUID.randomUUID(), 1, 10L);
        PurchaseEntity second = purchase(studentId, productId, UUID.randomUUID(), 1, 12L);

        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(student.getStatus()).thenReturn("ACTIVE");
        when(student.getRoomId()).thenReturn(roomId);
        when(student.getId()).thenReturn(studentId);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(roomId);
        when(room.getCurrentStage()).thenReturn("DAY2_FINAL_RESULT");
        when(productRepository.findAllByRoomIdOrderByDisplayOrder(roomId)).thenReturn(List.of(product));
        when(product.getId()).thenReturn(productId);
        when(product.getName()).thenReturn("연필 세트");
        when(purchaseRepository.findAllByStudentIdOrderByCreatedAtDesc(studentId))
                .thenReturn(List.of(second, first));

        var result = service.studentResult(studentId);

        assertEquals(1, result.purchases().size());
        assertEquals(productId, result.purchases().getFirst().productId());
        assertEquals(2, result.purchases().getFirst().quantity());
        assertEquals(22L, result.purchases().getFirst().totalPrice());
        assertEquals(22L, result.totalPurchaseAmount());
    }

    @Test
    void combinesTeacherDistributionByDayStudentAndProduct() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID dayOneSessionId = UUID.randomUUID();
        UUID dayTwoSessionId = UUID.randomUUID();
        UUID firstTransactionId = UUID.randomUUID();
        UUID secondTransactionId = UUID.randomUUID();
        UUID thirdTransactionId = UUID.randomUUID();
        RoomEntity room = mock(RoomEntity.class);
        StudentEntity student = mock(StudentEntity.class);
        ProductEntity product = mock(ProductEntity.class);
        DaySessionEntity dayOne = session(dayOneSessionId, 1);
        DaySessionEntity dayTwo = session(dayTwoSessionId, 2);
        FinancialTransactionEntity firstTransaction = transaction(firstTransactionId, dayOneSessionId);
        FinancialTransactionEntity secondTransaction = transaction(secondTransactionId, dayOneSessionId);
        FinancialTransactionEntity thirdTransaction = transaction(thirdTransactionId, dayTwoSessionId);
        PurchaseEntity first = purchase(studentId, productId, firstTransactionId, 1, 10L);
        PurchaseEntity second = purchase(studentId, productId, secondTransactionId, 1, 10L);
        PurchaseEntity third = purchase(studentId, productId, thirdTransactionId, 1, 12L);

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getCurrentStage()).thenReturn("DAY2_FINAL_RESULT");
        when(productRepository.findAllByRoomIdOrderByDisplayOrder(roomId)).thenReturn(List.of(product));
        when(product.getId()).thenReturn(productId);
        when(product.getName()).thenReturn("연필 세트");
        when(studentRepository.findAllByRoomIdOrderByParticipantNumber(roomId)).thenReturn(List.of(student));
        when(student.getId()).thenReturn(studentId);
        when(student.getParticipantNumber()).thenReturn(3);
        when(student.getNickname()).thenReturn("비빔이");
        when(purchaseRepository.findAllByRoomIdOrderByCreatedAtDesc(roomId))
                .thenReturn(List.of(third, second, first));
        when(sessionRepository.findByRoomIdAndDayNumber(roomId, (short) 1)).thenReturn(Optional.of(dayOne));
        when(sessionRepository.findByRoomIdAndDayNumber(roomId, (short) 2)).thenReturn(Optional.of(dayTwo));
        when(transactionRepository.findAllById(any()))
                .thenReturn(List.of(firstTransaction, secondTransaction, thirdTransaction));

        var result = service.productDistribution(teacherId, roomId);

        assertEquals(3, result.totalQuantity());
        assertEquals(2, result.items().size());
        assertEquals(1, result.items().getFirst().dayNumber());
        assertEquals(2, result.items().getFirst().quantity());
        assertEquals(20L, result.items().getFirst().paidAmount());
        assertEquals(2, result.items().getLast().dayNumber());
        assertEquals(1, result.items().getLast().quantity());
        assertEquals(12L, result.items().getLast().paidAmount());
    }

    private PurchaseEntity purchase(
            UUID studentId,
            UUID productId,
            UUID transactionId,
            int quantity,
            long totalPrice
    ) {
        PurchaseEntity purchase = mock(PurchaseEntity.class);
        when(purchase.getStatus()).thenReturn("SUCCESS");
        lenient().when(purchase.getStudentId()).thenReturn(studentId);
        when(purchase.getProductId()).thenReturn(productId);
        lenient().when(purchase.getTransactionId()).thenReturn(transactionId);
        when(purchase.getQuantity()).thenReturn(quantity);
        when(purchase.getTotalPrice()).thenReturn(totalPrice);
        return purchase;
    }

    private DaySessionEntity session(UUID sessionId, int dayNumber) {
        DaySessionEntity session = mock(DaySessionEntity.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.getDayNumber()).thenReturn(dayNumber);
        return session;
    }

    private FinancialTransactionEntity transaction(UUID transactionId, UUID sessionId) {
        FinancialTransactionEntity transaction = mock(FinancialTransactionEntity.class);
        when(transaction.getId()).thenReturn(transactionId);
        when(transaction.getSessionId()).thenReturn(sessionId);
        return transaction;
    }
}
