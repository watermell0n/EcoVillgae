package com.b201.bibim.feature.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.b201.bibim.domain.finance.AccountEntity;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.finance.FinancialEntryRepository;
import com.b201.bibim.domain.finance.FinancialTransactionEntity;
import com.b201.bibim.domain.finance.FinancialTransactionRepository;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.shop.ProductEntity;
import com.b201.bibim.domain.shop.ProductRepository;
import com.b201.bibim.domain.shop.PurchaseEntity;
import com.b201.bibim.domain.shop.PurchaseRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.shop.ShopDto.PurchaseRequest;
import com.b201.bibim.feature.shop.ShopDto.PurchaseResponse;
import com.b201.bibim.global.exception.ApiException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock
    private RoomRepository roomRepository;
    @Mock
    private DaySessionRepository sessionRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private FinancialTransactionRepository transactionRepository;
    @Mock
    private FinancialEntryRepository entryRepository;
    @Mock
    private StudentEntity student;
    @Mock
    private RoomEntity room;
    @Mock
    private ProductEntity product;
    @Mock
    private AccountEntity wallet;
    @Mock
    private FinancialTransactionEntity transaction;
    @Mock
    private PurchaseEntity purchase;

    @InjectMocks
    private ShopService shopService;

    @Test
    void rejectsPurchaseWithoutChangingStockWhenBalanceIsInsufficient() {
        UUID studentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        prepareOpenStore(studentId, roomId);
        when(room.getCurrentStage()).thenReturn("DAY1_FREE_ACTIVITY");
        when(transactionRepository.findByIdempotencyKey("purchase-key"))
                .thenReturn(Optional.empty());
        when(accountRepository.findStudentAccountForUpdate(
                roomId,
                studentId,
                "STUDENT_WALLET"
        )).thenReturn(Optional.of(wallet));
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
        when(product.getRoomId()).thenReturn(roomId);
        when(product.getStatus()).thenReturn("ON_SALE");
        when(product.getCurrentStock()).thenReturn(5);
        when(product.getCurrentPrice()).thenReturn(25L);
        when(wallet.getBalance()).thenReturn(10L);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> shopService.purchase(
                        studentId,
                        "purchase-key",
                        new PurchaseRequest(productId, 1)
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("INSUFFICIENT_BALANCE", exception.getCode());
        verify(product, never()).sell(1);
        verify(transactionRepository, never()).save(
                org.mockito.ArgumentMatchers.any(FinancialTransactionEntity.class)
        );
    }

    @Test
    void replaysCompletedPurchaseWithoutChargingAgain() {
        UUID studentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        prepareOpenStore(studentId, roomId);
        when(student.getId()).thenReturn(studentId);
        when(transactionRepository.findByIdempotencyKey("retry-key"))
                .thenReturn(Optional.of(transaction));
        when(transaction.getTransactionType()).thenReturn("PURCHASE");
        when(transaction.getStudentId()).thenReturn(studentId);
        when(transaction.getId()).thenReturn(transactionId);
        when(purchaseRepository.findByTransactionId(transactionId))
                .thenReturn(Optional.of(purchase));
        when(purchase.getId()).thenReturn(purchaseId);
        when(purchase.getProductId()).thenReturn(productId);
        when(purchase.getQuantity()).thenReturn(1);
        when(purchase.getUnitPrice()).thenReturn(25L);
        when(purchase.getTotalPrice()).thenReturn(25L);
        when(purchase.getCreatedAt()).thenReturn(Instant.now());
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(product.getName()).thenReturn("연필 세트");
        when(product.getCurrentStock()).thenReturn(9);
        when(product.getStatus()).thenReturn("ON_SALE");
        when(accountRepository.findByRoomIdAndStudentIdAndAccountType(
                roomId,
                studentId,
                "STUDENT_WALLET"
        )).thenReturn(Optional.of(wallet));
        when(wallet.getBalance()).thenReturn(75L);

        PurchaseResponse response = shopService.purchase(
                studentId,
                "retry-key",
                new PurchaseRequest(productId, 1)
        );

        assertTrue(response.replayed());
        assertEquals(75L, response.walletBalance());
        assertEquals(9, response.remainingStock());
        verify(product, never()).sell(1);
        verify(transactionRepository, never()).save(
                org.mockito.ArgumentMatchers.any(FinancialTransactionEntity.class)
        );
    }

    private void prepareOpenStore(UUID studentId, UUID roomId) {
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(student.getStatus()).thenReturn("ACTIVE");
        when(student.getRoomId()).thenReturn(roomId);
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(roomId);
        when(studentRepository.findByIdForUpdate(studentId)).thenReturn(Optional.of(student));
    }
}
