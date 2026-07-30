package com.b201.bibim.feature.character;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.b201.bibim.domain.character.CharacterBodyState;
import com.b201.bibim.domain.character.CharacterCosmeticEntity;
import com.b201.bibim.domain.character.CharacterCosmeticOwnershipEntity;
import com.b201.bibim.domain.character.CharacterCosmeticOwnershipRepository;
import com.b201.bibim.domain.character.CharacterCosmeticRepository;
import com.b201.bibim.domain.character.StudentCharacterEntity;
import com.b201.bibim.domain.character.StudentCharacterRepository;
import com.b201.bibim.domain.finance.AccountEntity;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.finance.FinancialEntryEntity;
import com.b201.bibim.domain.finance.FinancialEntryRepository;
import com.b201.bibim.domain.finance.FinancialTransactionEntity;
import com.b201.bibim.domain.finance.FinancialTransactionRepository;
import com.b201.bibim.domain.room.DaySessionEntity;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.shop.PurchaseEntity;
import com.b201.bibim.domain.shop.PurchaseRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.character.CharacterDto.CharacterOverviewResponse;
import com.b201.bibim.feature.character.CharacterDto.CharacterResponse;
import com.b201.bibim.feature.character.CharacterDto.CosmeticItemResponse;
import com.b201.bibim.feature.character.CharacterDto.EquipRequest;
import com.b201.bibim.feature.character.CharacterDto.PurchaseRequest;
import com.b201.bibim.feature.character.CharacterDto.PurchaseResponse;
import com.b201.bibim.feature.character.CharacterDto.RenameRequest;
import com.b201.bibim.feature.flow.StudentFlowService;
import com.b201.bibim.global.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CharacterService {

    // 구매(돈 이동)는 상점과 동일한 스테이지 게이팅을 적용한다.
    private static final Set<String> OPEN_STAGES = Set.of(
            "DAY1_FREE_ACTIVITY",
            "DAY2_FINAL_SHOP"
    );

    private static final Set<String> SLOTS = Set.of(
            "HAT", "FACE", "NECK", "TOP", "SHOES",
            "BACK", "HAND", "EFFECT", "BACKGROUND"
    );

    // 방 종료(조회 전용)를 판정하는 스테이지/상태.
    private static final String COMPLETED_STAGE = "DAY2_COMPLETED";

    // 부적절어 간이 필터 (기획: '간단한 금칙어 목록 정도').
    private static final Set<String> BANNED_WORDS = Set.of(
            "바보", "멍청이", "병신", "시발", "씨발", "새끼", "죽어", "꺼져", "지랄",
            "fuck", "shit", "bitch", "asshole", "sex"
    );

    private final RoomRepository roomRepository;
    private final DaySessionRepository sessionRepository;
    private final StudentRepository studentRepository;
    private final StudentCharacterRepository characterRepository;
    private final CharacterCosmeticRepository cosmeticRepository;
    private final CharacterCosmeticOwnershipRepository ownershipRepository;
    private final PurchaseRepository purchaseRepository;
    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final FinancialEntryRepository entryRepository;
    private final StudentFlowService studentFlowService;

    public CharacterService(
            RoomRepository roomRepository,
            DaySessionRepository sessionRepository,
            StudentRepository studentRepository,
            StudentCharacterRepository characterRepository,
            CharacterCosmeticRepository cosmeticRepository,
            CharacterCosmeticOwnershipRepository ownershipRepository,
            PurchaseRepository purchaseRepository,
            AccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            FinancialEntryRepository entryRepository,
            StudentFlowService studentFlowService
    ) {
        this.roomRepository = roomRepository;
        this.sessionRepository = sessionRepository;
        this.studentRepository = studentRepository;
        this.characterRepository = characterRepository;
        this.cosmeticRepository = cosmeticRepository;
        this.ownershipRepository = ownershipRepository;
        this.purchaseRepository = purchaseRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
        this.studentFlowService = studentFlowService;
    }

    @Transactional
    public CharacterOverviewResponse overview(UUID studentId) {
        StudentEntity student = findActiveStudent(studentId);
        RoomEntity room = roomRepository.findById(student.getRoomId())
                .orElseThrow(this::roomNotFound);
        StudentCharacterEntity character = getOrCreateCharacter(room.getId(), studentId);

        Set<UUID> ownedIds = ownershipRepository.findAllByCharacterId(character.getId()).stream()
                .map(CharacterCosmeticOwnershipEntity::getCosmeticId)
                .collect(Collectors.toSet());
        Map<String, Object> equipped = character.getEquipped();
        Set<String> equippedCosmeticIds = equipped.values().stream()
                .map(String::valueOf)
                .collect(Collectors.toSet());

        List<CosmeticItemResponse> cosmetics = cosmeticRepository
                .findAllByRoomIdOrderByDisplayOrder(room.getId())
                .stream()
                .filter(cosmetic -> !"DISCONTINUED".equals(cosmetic.getStatus()))
                .map(cosmetic -> new CosmeticItemResponse(
                        cosmetic.getId(),
                        cosmetic.getName(),
                        cosmetic.getSlot(),
                        cosmetic.getPrice(),
                        cosmetic.getSpriteKey(),
                        cosmetic.getTheme(),
                        cosmetic.getAnchor(),
                        cosmetic.getDisplayOrder(),
                        cosmetic.getStatus(),
                        ownedIds.contains(cosmetic.getId()),
                        equippedCosmeticIds.contains(cosmetic.getId().toString())
                ))
                .toList();

        return new CharacterOverviewResponse(
                character.getId(),
                character.getName(),
                walletBalance(room.getId(), studentId),
                bodyState(room.getId(), studentId, character.getId()),
                equipped,
                cosmetics
        );
    }

    @Transactional
    public PurchaseResponse purchase(
            UUID studentId,
            String idempotencyKey,
            PurchaseRequest request
    ) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        StudentEntity snapshot = findActiveStudent(studentId);
        RoomEntity room = roomRepository.findByIdForUpdate(snapshot.getRoomId())
                .orElseThrow(this::roomNotFound);
        StudentEntity student = studentRepository.findByIdForUpdate(studentId)
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .filter(item -> room.getId().equals(item.getRoomId()))
                .orElseThrow(this::authenticationRequired);

        var existingTransaction = transactionRepository.findByIdempotencyKey(normalizedKey);
        if (existingTransaction.isPresent()) {
            return replayPurchase(existingTransaction.get(), student, request);
        }

        ensurePurchaseOpen(room, studentId);
        StudentCharacterEntity character = getOrCreateCharacterForUpdate(room.getId(), studentId);
        AccountEntity wallet = accountRepository.findStudentAccountForUpdate(
                        room.getId(),
                        studentId,
                        "STUDENT_WALLET"
                )
                .orElseThrow(this::insufficientBalance);
        CharacterCosmeticEntity cosmetic = cosmeticRepository.findByIdForUpdate(request.cosmeticId())
                .filter(item -> room.getId().equals(item.getRoomId()))
                .orElseThrow(this::cosmeticNotFound);
        ensureCosmeticAvailable(cosmetic);

        if (ownershipRepository
                .findByCharacterIdAndCosmeticId(character.getId(), cosmetic.getId())
                .isPresent()) {
            throw alreadyOwned();
        }

        long price = cosmetic.getPrice();
        if (wallet.getBalance() < price) {
            throw insufficientBalance();
        }

        DaySessionEntity session = sessionRepository
                .findByRoomIdAndDayNumber(room.getId(), (short) room.getCurrentDay())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "SESSION_NOT_FOUND",
                        "현재 수업 세션을 찾을 수 없습니다."
                ));
        AccountEntity characterExpense = getOrCreateCharacterExpense(room.getId());
        FinancialTransactionEntity transaction = transactionRepository.save(
                FinancialTransactionEntity.studentTransfer(
                        room.getId(),
                        session.getId(),
                        studentId,
                        "COSMETIC_PURCHASE",
                        normalizedKey,
                        cosmetic.getName() + " 구매",
                        Map.of(
                                "characterId", character.getId().toString(),
                                "cosmeticId", cosmetic.getId().toString(),
                                "slot", cosmetic.getSlot(),
                                "price", price
                        )
                )
        );

        long walletBefore = wallet.getBalance();
        long expenseBefore = characterExpense.getBalance();
        wallet.changeBy(-price);
        characterExpense.changeBy(price);
        entryRepository.saveAll(List.of(
                FinancialEntryEntity.create(transaction.getId(), wallet, -price, walletBefore, 1),
                FinancialEntryEntity.create(
                        transaction.getId(), characterExpense, price, expenseBefore, 2
                )
        ));
        CharacterCosmeticOwnershipEntity ownership = ownershipRepository.save(
                CharacterCosmeticOwnershipEntity.create(
                        character.getId(), cosmetic.getId(), transaction.getId(), price
                )
        );
        room.markEconomicStateChanged();

        return new PurchaseResponse(
                ownership.getId(),
                cosmetic.getId(),
                cosmetic.getName(),
                cosmetic.getSlot(),
                ownership.getPricePaid(),
                wallet.getBalance(),
                bodyState(room.getId(), studentId, character.getId()),
                false
        );
    }

    @Transactional
    public CharacterResponse equip(UUID studentId, EquipRequest request) {
        StudentEntity student = findActiveStudent(studentId);
        RoomEntity room = roomRepository.findById(student.getRoomId())
                .orElseThrow(this::roomNotFound);
        ensureRoomLive(room);
        StudentCharacterEntity character = getOrCreateCharacterForUpdate(room.getId(), studentId);

        String slot = normalizeSlot(request.slot());
        if (request.cosmeticId() == null) {
            character.equip(slot, null);
        } else {
            CharacterCosmeticEntity cosmetic = cosmeticRepository.findById(request.cosmeticId())
                    .filter(item -> room.getId().equals(item.getRoomId()))
                    .orElseThrow(this::cosmeticNotFound);
            if (!slot.equals(cosmetic.getSlot())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "SLOT_MISMATCH",
                        "코스메틱 슬롯과 착용 슬롯이 일치하지 않습니다."
                );
            }
            ownershipRepository
                    .findByCharacterIdAndCosmeticId(character.getId(), cosmetic.getId())
                    .orElseThrow(this::notOwned);
            character.equip(slot, cosmetic.getId());
        }

        return toCharacterResponse(room.getId(), studentId, character);
    }

    @Transactional
    public CharacterResponse rename(UUID studentId, RenameRequest request) {
        StudentEntity student = findActiveStudent(studentId);
        RoomEntity room = roomRepository.findById(student.getRoomId())
                .orElseThrow(this::roomNotFound);
        ensureRoomLive(room);
        StudentCharacterEntity character = getOrCreateCharacterForUpdate(room.getId(), studentId);
        character.rename(sanitizeName(request.name()));
        return toCharacterResponse(room.getId(), studentId, character);
    }

    private PurchaseResponse replayPurchase(
            FinancialTransactionEntity transaction,
            StudentEntity student,
            PurchaseRequest request
    ) {
        if (!"COSMETIC_PURCHASE".equals(transaction.getTransactionType())
                || !student.getId().equals(transaction.getStudentId())) {
            throw idempotencyKeyReused();
        }
        CharacterCosmeticOwnershipEntity ownership = ownershipRepository
                .findByTransactionId(transaction.getId())
                .orElseThrow(this::idempotencyKeyReused);
        if (!request.cosmeticId().equals(ownership.getCosmeticId())) {
            throw idempotencyKeyReused();
        }
        CharacterCosmeticEntity cosmetic = cosmeticRepository.findById(ownership.getCosmeticId())
                .orElseThrow(this::cosmeticNotFound);
        return new PurchaseResponse(
                ownership.getId(),
                cosmetic.getId(),
                cosmetic.getName(),
                cosmetic.getSlot(),
                ownership.getPricePaid(),
                walletBalance(student.getRoomId(), student.getId()),
                bodyState(student.getRoomId(), student.getId(), ownership.getCharacterId()),
                true
        );
    }

    private CharacterResponse toCharacterResponse(
            UUID roomId, UUID studentId, StudentCharacterEntity character
    ) {
        return new CharacterResponse(
                character.getId(),
                character.getName(),
                bodyState(roomId, studentId, character.getId()),
                character.getEquipped()
        );
    }

    private StudentCharacterEntity getOrCreateCharacter(UUID roomId, UUID studentId) {
        return characterRepository.findByRoomIdAndStudentId(roomId, studentId)
                .orElseGet(() -> characterRepository.saveAndFlush(
                        StudentCharacterEntity.create(roomId, studentId)
                ));
    }

    private StudentCharacterEntity getOrCreateCharacterForUpdate(UUID roomId, UUID studentId) {
        return characterRepository.findByRoomIdAndStudentIdForUpdate(roomId, studentId)
                .orElseGet(() -> characterRepository.saveAndFlush(
                        StudentCharacterEntity.create(roomId, studentId)
                ));
    }

    private String bodyState(UUID roomId, UUID studentId, UUID characterId) {
        long wallet = walletBalance(roomId, studentId);
        long cosmeticSpend = ownershipRepository.findAllByCharacterId(characterId).stream()
                .mapToLong(CharacterCosmeticOwnershipEntity::getPricePaid)
                .sum();
        long shopSpend = purchaseRepository.findAllByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .filter(purchase -> "SUCCESS".equals(purchase.getStatus()))
                .mapToLong(PurchaseEntity::getTotalPrice)
                .sum();
        return CharacterBodyState.evaluate(wallet, cosmeticSpend + shopSpend).name();
    }

    private long walletBalance(UUID roomId, UUID studentId) {
        return accountRepository.findByRoomIdAndStudentIdAndAccountType(
                        roomId, studentId, "STUDENT_WALLET"
                )
                .map(AccountEntity::getBalance)
                .orElse(0L);
    }

    private AccountEntity getOrCreateCharacterExpense(UUID roomId) {
        return accountRepository.findRoomAccountForUpdate(roomId, "CHARACTER_EXPENSE")
                .orElseGet(() -> accountRepository.saveAndFlush(
                        AccountEntity.characterExpense(roomId)
                ));
    }

    private void ensurePurchaseOpen(RoomEntity room, UUID studentId) {
        boolean catchUpStore = studentFlowService.canUseStage(studentId, "DAY1_FREE_ACTIVITY")
                || studentFlowService.canUseStage(studentId, "DAY2_FINAL_SHOP");
        if (!OPEN_STAGES.contains(room.getCurrentStage()) && !catchUpStore) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "STORE_CLOSED",
                    "현재는 코스메틱을 구매할 수 있는 시간이 아닙니다."
            );
        }
    }

    private void ensureRoomLive(RoomEntity room) {
        if (COMPLETED_STAGE.equals(room.getCurrentStage())
                || "COMPLETED".equals(room.getStatus())
                || "ARCHIVED".equals(room.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ROOM_CLOSED",
                    "수업이 종료되어 캐릭터를 변경할 수 없습니다."
            );
        }
    }

    private void ensureCosmeticAvailable(CharacterCosmeticEntity cosmetic) {
        if (!cosmetic.isOnSale()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "COSMETIC_UNAVAILABLE",
                    "현재 판매 중인 코스메틱이 아닙니다."
            );
        }
    }

    private String normalizeSlot(String slot) {
        String normalized = slot == null ? "" : slot.trim().toUpperCase();
        if (!SLOTS.contains(normalized)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SLOT",
                    "알 수 없는 코스메틱 슬롯입니다."
            );
        }
        return normalized;
    }

    private String sanitizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_CHARACTER_NAME",
                    "캐릭터 이름을 입력해 주세요."
            );
        }
        if (normalized.length() > 20) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_CHARACTER_NAME",
                    "캐릭터 이름은 20자 이하여야 합니다."
            );
        }
        String lowered = normalized.toLowerCase();
        for (String banned : BANNED_WORDS) {
            if (lowered.contains(banned)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INAPPROPRIATE_CHARACTER_NAME",
                        "사용할 수 없는 표현이 포함되어 있습니다."
                );
            }
        }
        return normalized;
    }

    private StudentEntity findActiveStudent(UUID studentId) {
        return studentRepository.findById(studentId)
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .orElseThrow(this::authenticationRequired);
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key 헤더가 필요합니다."
            );
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 100) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
