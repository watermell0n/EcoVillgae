package com.b201.bibim.feature.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.b201.bibim.domain.character.CharacterBodyState;
import com.b201.bibim.domain.character.CharacterCosmeticEntity;
import com.b201.bibim.domain.character.CharacterCosmeticOwnershipEntity;
import com.b201.bibim.domain.character.CharacterCosmeticOwnershipRepository;
import com.b201.bibim.domain.character.CharacterCosmeticRepository;
import com.b201.bibim.domain.character.StudentCharacterEntity;
import com.b201.bibim.domain.character.StudentCharacterRepository;
import com.b201.bibim.domain.finance.AccountEntity;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.finance.FinancialEntryRepository;
import com.b201.bibim.domain.finance.FinancialTransactionEntity;
import com.b201.bibim.domain.finance.FinancialTransactionRepository;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.shop.PurchaseRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.character.CharacterDto.CharacterResponse;
import com.b201.bibim.feature.character.CharacterDto.EquipRequest;
import com.b201.bibim.feature.character.CharacterDto.PurchaseRequest;
import com.b201.bibim.feature.character.CharacterDto.PurchaseResponse;
import com.b201.bibim.feature.flow.StudentFlowService;
import com.b201.bibim.global.exception.ApiException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CharacterServiceTest {

    @Mock
    private RoomRepository roomRepository;
    @Mock
    private DaySessionRepository sessionRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private StudentCharacterRepository characterRepository;
    @Mock
    private CharacterCosmeticRepository cosmeticRepository;
    @Mock
    private CharacterCosmeticOwnershipRepository ownershipRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private FinancialTransactionRepository transactionRepository;
    @Mock
    private FinancialEntryRepository entryRepository;
    @Mock
    private StudentFlowService studentFlowService;

    @Mock
    private StudentEntity student;
    @Mock
    private RoomEntity room;
    @Mock
    private StudentCharacterEntity character;
    @Mock
    private CharacterCosmeticEntity cosmetic;
    @Mock
    private AccountEntity wallet;
    @Mock
    private FinancialTransactionEntity transaction;
    @Mock
    private CharacterCosmeticOwnershipEntity ownership;

    @InjectMocks
    private CharacterService characterService;

    @Test
    void rejectsPurchaseWithoutLedgerWhenBalanceIsInsufficient() {
        UUID studentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        UUID cosmeticId = UUID.randomUUID();
        prepareLockedStudentAndRoom(studentId, roomId);
        when(room.getCurrentStage()).thenReturn("DAY1_FREE_ACTIVITY");
        when(transactionRepository.findByIdempotencyKey("buy-key")).thenReturn(Optional.empty());
        when(characterRepository.findByRoomIdAndStudentIdForUpdate(roomId, studentId))
                .thenReturn(Optional.of(character));
        when(character.getId()).thenReturn(characterId);
        when(accountRepository.findStudentAccountForUpdate(roomId, studentId, "STUDENT_WALLET"))
                .thenReturn(Optional.of(wallet));
        when(cosmeticRepository.findByIdForUpdate(cosmeticId)).thenReturn(Optional.of(cosmetic));
        when(cosmetic.getRoomId()).thenReturn(roomId);
        when(cosmetic.isOnSale()).thenReturn(true);
        when(cosmetic.getId()).thenReturn(cosmeticId);
        when(ownershipRepository.findByCharacterIdAndCosmeticId(characterId, cosmeticId))
                .thenReturn(Optional.empty());
        when(cosmetic.getPrice()).thenReturn(30L);
        when(wallet.getBalance()).thenReturn(10L);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> characterService.purchase(
                        studentId, "buy-key", new PurchaseRequest(cosmeticId)
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("INSUFFICIENT_BALANCE", exception.getCode());
        verify(transactionRepository, never()).save(any(FinancialTransactionEntity.class));
        verify(ownershipRepository, never()).save(any(CharacterCosmeticOwnershipEntity.class));
    }

    @Test
    void rejectsDuplicateOwnershipWithNewKey() {
        UUID studentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        UUID cosmeticId = UUID.randomUUID();
        prepareLockedStudentAndRoom(studentId, roomId);
        when(room.getCurrentStage()).thenReturn("DAY1_FREE_ACTIVITY");
        when(transactionRepository.findByIdempotencyKey("new-key")).thenReturn(Optional.empty());
        when(characterRepository.findByRoomIdAndStudentIdForUpdate(roomId, studentId))
                .thenReturn(Optional.of(character));
        when(character.getId()).thenReturn(characterId);
        when(accountRepository.findStudentAccountForUpdate(roomId, studentId, "STUDENT_WALLET"))
                .thenReturn(Optional.of(wallet));
        when(cosmeticRepository.findByIdForUpdate(cosmeticId)).thenReturn(Optional.of(cosmetic));
        when(cosmetic.getRoomId()).thenReturn(roomId);
        when(cosmetic.isOnSale()).thenReturn(true);
        when(cosmetic.getId()).thenReturn(cosmeticId);
        when(ownershipRepository.findByCharacterIdAndCosmeticId(characterId, cosmeticId))
                .thenReturn(Optional.of(ownership));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> characterService.purchase(
                        studentId, "new-key", new PurchaseRequest(cosmeticId)
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("ALREADY_OWNED", exception.getCode());
        verify(transactionRepository, never()).save(any(FinancialTransactionEntity.class));
    }

    @Test
    void replaysCompletedPurchaseWithSameKeyWithoutChargingAgain() {
        UUID studentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        UUID cosmeticId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID ownershipId = UUID.randomUUID();
        prepareLockedStudentAndRoom(studentId, roomId);
        when(student.getId()).thenReturn(studentId);
        when(transactionRepository.findByIdempotencyKey("retry-key"))
                .thenReturn(Optional.of(transaction));
        when(transaction.getTransactionType()).thenReturn("COSMETIC_PURCHASE");
        when(transaction.getStudentId()).thenReturn(studentId);
        when(transaction.getId()).thenReturn(transactionId);
        when(ownershipRepository.findByTransactionId(transactionId))
                .thenReturn(Optional.of(ownership));
        when(ownership.getCosmeticId()).thenReturn(cosmeticId);
        when(ownership.getId()).thenReturn(ownershipId);
        when(ownership.getPricePaid()).thenReturn(30L);
        when(ownership.getCharacterId()).thenReturn(characterId);
        when(cosmeticRepository.findById(cosmeticId)).thenReturn(Optional.of(cosmetic));
        when(cosmetic.getId()).thenReturn(cosmeticId);
        when(cosmetic.getName()).thenReturn("빨간 캡");
        when(cosmetic.getSlot()).thenReturn("FACE");
        when(accountRepository.findByRoomIdAndStudentIdAndAccountType(
                roomId, studentId, "STUDENT_WALLET")).thenReturn(Optional.of(wallet));
        when(wallet.getBalance()).thenReturn(70L);
        when(ownershipRepository.findAllByCharacterId(characterId)).thenReturn(List.of());
        when(purchaseRepository.findAllByStudentIdOrderByCreatedAtDesc(studentId))
                .thenReturn(List.of());

        PurchaseResponse response = characterService.purchase(
                studentId, "retry-key", new PurchaseRequest(cosmeticId)
        );

        assertTrue(response.replayed());
        assertEquals(70L, response.walletBalance());
        assertEquals("빨간 캡", response.cosmeticName());
        verify(transactionRepository, never()).save(any(FinancialTransactionEntity.class));
        verify(entryRepository, never()).saveAll(any());
    }

    @Test
    void equipUpdatesLoadoutWithoutMovingMoney() {
        UUID studentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        UUID cosmeticId = UUID.randomUUID();
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(student.getStatus()).thenReturn("ACTIVE");
        when(student.getRoomId()).thenReturn(roomId);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(roomId);
        when(room.getCurrentStage()).thenReturn("DAY1_FREE_ACTIVITY");
        when(characterRepository.findByRoomIdAndStudentIdForUpdate(roomId, studentId))
                .thenReturn(Optional.of(character));
        when(character.getId()).thenReturn(characterId);
        when(cosmeticRepository.findById(cosmeticId)).thenReturn(Optional.of(cosmetic));
        when(cosmetic.getRoomId()).thenReturn(roomId);
        when(cosmetic.getSlot()).thenReturn("HAT");
        when(cosmetic.getId()).thenReturn(cosmeticId);
        when(ownershipRepository.findByCharacterIdAndCosmeticId(characterId, cosmeticId))
                .thenReturn(Optional.of(ownership));
        when(ownershipRepository.findAllByCharacterId(characterId)).thenReturn(List.of());
        when(purchaseRepository.findAllByStudentIdOrderByCreatedAtDesc(studentId))
                .thenReturn(List.of());

        CharacterResponse response = characterService.equip(
                studentId, new EquipRequest("FACE", cosmeticId)
        );

        assertFalse(response == null);
        verify(character).equip("FACE", cosmeticId);
        verify(transactionRepository, never()).save(any(FinancialTransactionEntity.class));
        verify(entryRepository, never()).saveAll(any());
        verify(accountRepository, never()).saveAndFlush(any(AccountEntity.class));
    }

    @Test
    void bodyStateReflectsSaveAndSpendRatiosWithHysteresis() {
        // denominator = income_base(100) * days(2) = 200
        assertEquals(CharacterBodyState.FAT, CharacterBodyState.evaluate(200L, 0L));
        assertEquals(CharacterBodyState.SLIM, CharacterBodyState.evaluate(0L, 200L));
        assertEquals(CharacterBodyState.BASE, CharacterBodyState.evaluate(50L, 50L));
        // 히스테리시스: FAT 상태에서 save_ratio 0.55(<0.6 진입, >=0.5 유지) → FAT 유지
        assertEquals(
                CharacterBodyState.FAT,
                CharacterBodyState.evaluate(CharacterBodyState.FAT, 110L, 0L)
        );
        // BASE 에서 save_ratio 0.55 는 진입 임계값(0.6) 미만 → BASE
        assertEquals(
                CharacterBodyState.BASE,
                CharacterBodyState.evaluate(CharacterBodyState.BASE, 110L, 0L)
        );
    }

    private void prepareLockedStudentAndRoom(UUID studentId, UUID roomId) {
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(student.getStatus()).thenReturn("ACTIVE");
        when(student.getRoomId()).thenReturn(roomId);
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(roomId);
        when(studentRepository.findByIdForUpdate(studentId)).thenReturn(Optional.of(student));
    }
}
