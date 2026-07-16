package com.b201.bibim.feature.deposit;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.b201.bibim.domain.deposit.DepositEntity;
import com.b201.bibim.domain.deposit.DepositRepository;
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
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.deposit.DepositDto.DepositResponse;
import com.b201.bibim.feature.deposit.DepositDto.SaveDraftRequest;
import com.b201.bibim.global.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepositService {

    private static final int INTEREST_RATE_BPS = 500;
    private static final long MINIMUM_PRINCIPAL = 20;
    private static final long AMOUNT_UNIT = 10;
    private static final String OPEN_STAGE = "DAY1_FREE_ACTIVITY";

    private final DepositRepository depositRepository;
    private final StudentRepository studentRepository;
    private final RoomRepository roomRepository;
    private final DaySessionRepository sessionRepository;
    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final FinancialEntryRepository entryRepository;

    public DepositService(
            DepositRepository depositRepository,
            StudentRepository studentRepository,
            RoomRepository roomRepository,
            DaySessionRepository sessionRepository,
            AccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            FinancialEntryRepository entryRepository
    ) {
        this.depositRepository = depositRepository;
        this.studentRepository = studentRepository;
        this.roomRepository = roomRepository;
        this.sessionRepository = sessionRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
    }

    @Transactional(readOnly = true)
    public DepositResponse current(UUID studentId) {
        StudentEntity student = findActiveStudent(studentId);
        RoomEntity room = findRoom(student.getRoomId());
        ensureOpen(room);
        DaySessionEntity session = dayOneSession(room.getId());
        return depositRepository.findBySessionIdAndStudentId(session.getId(), studentId)
                .map(deposit -> toResponse(deposit, walletBalance(room.getId(), studentId), false))
                .orElseGet(() -> emptyResponse(walletBalance(room.getId(), studentId)));
    }

    @Transactional
    public DepositResponse saveDraft(UUID studentId, SaveDraftRequest request) {
        validatePrincipal(request.principal());
        StudentEntity snapshot = findActiveStudent(studentId);
        RoomEntity room = lockRoom(snapshot.getRoomId());
        StudentEntity student = lockActiveStudent(studentId);
        ensureOpen(room);
        AccountEntity wallet = walletForUpdate(room.getId(), studentId);
        if (wallet.getBalance() < request.principal()) {
            throw insufficientBalance();
        }
        DaySessionEntity session = dayOneSession(room.getId());
        long interest = expectedInterest(request.principal());
        DepositEntity deposit = depositRepository.findForUpdate(session.getId(), studentId)
                .map(existing -> {
                    if ("CONFIRMED".equals(existing.getStatus())) {
                        throw alreadyConfirmed();
                    }
                    existing.changeDraft(request.principal(), INTEREST_RATE_BPS, interest);
                    return existing;
                })
                .orElseGet(() -> DepositEntity.draft(
                        room.getId(),
                        session.getId(),
                        studentId,
                        request.principal(),
                        INTEREST_RATE_BPS,
                        interest
                ));
        depositRepository.save(deposit);
        room.markEconomicStateChanged();
        return toResponse(deposit, wallet.getBalance(), false);
    }

    @Transactional
    public DepositResponse cancelDraft(UUID studentId) {
        StudentEntity snapshot = findActiveStudent(studentId);
        RoomEntity room = lockRoom(snapshot.getRoomId());
        StudentEntity student = lockActiveStudent(studentId);
        ensureOpen(room);
        DaySessionEntity session = dayOneSession(room.getId());
        DepositEntity deposit = depositRepository.findForUpdate(session.getId(), studentId)
                .orElseThrow(this::draftNotFound);
        if (!"DRAFT".equals(deposit.getStatus())) {
            throw alreadyConfirmed();
        }
        deposit.cancelDraft();
        room.markEconomicStateChanged();
        return toResponse(deposit, walletBalance(room.getId(), studentId), false);
    }

    @Transactional
    public DepositResponse confirm(UUID studentId, String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        StudentEntity snapshot = findActiveStudent(studentId);
        RoomEntity room = lockRoom(snapshot.getRoomId());
        StudentEntity student = lockActiveStudent(studentId);
        ensureOpen(room);

        var existingTransaction = transactionRepository.findByIdempotencyKey(normalizedKey);
        if (existingTransaction.isPresent()) {
            FinancialTransactionEntity transaction = existingTransaction.get();
            if (!"DEPOSIT_CONFIRM".equals(transaction.getTransactionType())
                    || !studentId.equals(transaction.getStudentId())) {
                throw idempotencyKeyReused();
            }
            DepositEntity deposit = depositRepository.findByTransactionId(transaction.getId())
                    .orElseThrow(this::idempotencyKeyReused);
            return toResponse(deposit, walletBalance(room.getId(), studentId), true);
        }

        DaySessionEntity session = dayOneSession(room.getId());
        DepositEntity deposit = depositRepository.findForUpdate(session.getId(), studentId)
                .orElseThrow(this::draftNotFound);
        if (!"DRAFT".equals(deposit.getStatus())) {
            throw alreadyConfirmed();
        }
        AccountEntity wallet = walletForUpdate(room.getId(), studentId);
        if (wallet.getBalance() < deposit.getPrincipal()) {
            throw insufficientBalance();
        }
        AccountEntity depositAccount = accountRepository.findStudentAccountForUpdate(
                        room.getId(), studentId, "STUDENT_DEPOSIT"
                )
                .orElseGet(() -> accountRepository.saveAndFlush(
                        AccountEntity.studentDeposit(room.getId(), studentId)
                ));
        FinancialTransactionEntity transaction = transactionRepository.save(
                FinancialTransactionEntity.studentTransfer(
                        room.getId(),
                        session.getId(),
                        studentId,
                        "DEPOSIT_CONFIRM",
                        normalizedKey,
                        "1일차 예금 확정",
                        Map.of(
                                "principal", deposit.getPrincipal(),
                                "interestRateBps", deposit.getInterestRateBps(),
                                "expectedInterest", deposit.getExpectedInterest()
                        )
                )
        );
        long walletBefore = wallet.getBalance();
        long depositBefore = depositAccount.getBalance();
        wallet.changeBy(-deposit.getPrincipal());
        depositAccount.changeBy(deposit.getPrincipal());
        entryRepository.saveAll(List.of(
                FinancialEntryEntity.create(
                        transaction.getId(), wallet, -deposit.getPrincipal(), walletBefore, 1
                ),
                FinancialEntryEntity.create(
                        transaction.getId(), depositAccount, deposit.getPrincipal(), depositBefore, 2
                )
        ));
        deposit.confirm(transaction.getId());
        room.markEconomicStateChanged();
        return toResponse(deposit, wallet.getBalance(), false);
    }

    private DepositResponse toResponse(DepositEntity deposit, long walletBalance, boolean replayed) {
        return new DepositResponse(
                deposit.getId(),
                deposit.getStatus(),
                deposit.getPrincipal(),
                deposit.getInterestRateBps(),
                deposit.getExpectedInterest(),
                deposit.getPrincipal() + deposit.getExpectedInterest(),
                walletBalance,
                deposit.getUpdatedAt(),
                deposit.getConfirmedAt(),
                replayed
        );
    }

    private DepositResponse emptyResponse(long walletBalance) {
        return new DepositResponse(
                null, "NONE", 0, INTEREST_RATE_BPS, 0, 0,
                walletBalance, null, null, false
        );
    }

    private long expectedInterest(long principal) {
        try {
            return Math.addExact(Math.multiplyExact(principal, INTEREST_RATE_BPS), 5_000) / 10_000;
        } catch (ArithmeticException exception) {
            throw invalidAmount();
        }
    }

    private void validatePrincipal(long principal) {
        if (principal < MINIMUM_PRINCIPAL || principal % AMOUNT_UNIT != 0) {
            throw invalidAmount();
        }
    }

    private StudentEntity findActiveStudent(UUID studentId) {
        return studentRepository.findById(studentId)
                .filter(student -> "ACTIVE".equals(student.getStatus()))
                .orElseThrow(this::authenticationRequired);
    }

    private StudentEntity lockActiveStudent(UUID studentId) {
        return studentRepository.findByIdForUpdate(studentId)
                .filter(student -> "ACTIVE".equals(student.getStatus()))
                .orElseThrow(this::authenticationRequired);
    }

    private RoomEntity findRoom(UUID roomId) {
        return roomRepository.findById(roomId).orElseThrow(this::roomNotFound);
    }

    private RoomEntity lockRoom(UUID roomId) {
        return roomRepository.findByIdForUpdate(roomId).orElseThrow(this::roomNotFound);
    }

    private DaySessionEntity dayOneSession(UUID roomId) {
        return sessionRepository.findByRoomIdAndDayNumber(roomId, (short) 1)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT, "SESSION_NOT_FOUND", "1일차 수업을 찾을 수 없습니다."
                ));
    }

    private AccountEntity walletForUpdate(UUID roomId, UUID studentId) {
        return accountRepository.findStudentAccountForUpdate(
                roomId, studentId, "STUDENT_WALLET"
        ).orElseThrow(this::insufficientBalance);
    }

    private long walletBalance(UUID roomId, UUID studentId) {
        return accountRepository.findByRoomIdAndStudentIdAndAccountType(
                roomId, studentId, "STUDENT_WALLET"
        ).map(AccountEntity::getBalance).orElse(0L);
    }

    private void ensureOpen(RoomEntity room) {
        if (!OPEN_STAGE.equals(room.getCurrentStage())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DEPOSIT_CLOSED",
                    "현재는 예금을 저장하거나 확정할 수 없습니다."
            );
        }
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.trim().length() > 100) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IDEMPOTENCY_KEY_REQUIRED",
                    "100자 이하의 Idempotency-Key 헤더가 필요합니다."
            );
        }
        return key.trim();
    }

    private ApiException invalidAmount() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_DEPOSIT_AMOUNT",
                "예금액은 20비빔 이상, 10비빔 단위로 입력해 주세요."
        );
    }

    private ApiException insufficientBalance() {
        return new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_BALANCE", "지갑 잔액이 부족합니다.");
    }

    private ApiException alreadyConfirmed() {
        return new ApiException(HttpStatus.CONFLICT, "DEPOSIT_ALREADY_CONFIRMED", "확정된 예금은 변경하거나 취소할 수 없습니다.");
    }

    private ApiException draftNotFound() {
        return new ApiException(HttpStatus.CONFLICT, "DEPOSIT_DRAFT_NOT_FOUND", "먼저 예금 초안을 저장해 주세요.");
    }

    private ApiException idempotencyKeyReused() {
        return new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "같은 Idempotency-Key를 다른 요청에 사용할 수 없습니다.");
    }

    private ApiException authenticationRequired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
    }

    private ApiException roomNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "수업 방을 찾을 수 없습니다.");
    }
}
