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
import com.b201.bibim.domain.room.RoomRulesEntity;
import com.b201.bibim.domain.room.RoomRulesRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.deposit.DepositDto.DepositResponse;
import com.b201.bibim.feature.deposit.DepositDto.DepositHistoryItemResponse;
import com.b201.bibim.feature.deposit.DepositDto.DepositHistoryResponse;
import com.b201.bibim.feature.deposit.DepositDto.SaveDraftRequest;
import com.b201.bibim.global.exception.ApiException;
import com.b201.bibim.feature.flow.StudentFlowService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepositService {

    private static final String OPEN_STAGE = "DAY1_FREE_ACTIVITY";

    private final DepositRepository depositRepository;
    private final StudentRepository studentRepository;
    private final RoomRepository roomRepository;
    private final RoomRulesRepository rulesRepository;
    private final DaySessionRepository sessionRepository;
    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final FinancialEntryRepository entryRepository;
    private final StudentFlowService studentFlowService;

    public DepositService(
            DepositRepository depositRepository,
            StudentRepository studentRepository,
            RoomRepository roomRepository,
            RoomRulesRepository rulesRepository,
            DaySessionRepository sessionRepository,
            AccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            FinancialEntryRepository entryRepository,
            StudentFlowService studentFlowService
    ) {
        this.depositRepository = depositRepository;
        this.studentRepository = studentRepository;
        this.roomRepository = roomRepository;
        this.rulesRepository = rulesRepository;
        this.sessionRepository = sessionRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
        this.studentFlowService = studentFlowService;
    }

    @Transactional(readOnly = true)
    public DepositResponse current(UUID studentId) {
        StudentEntity student = findActiveStudent(studentId);
        RoomEntity room = findRoom(student.getRoomId());
        ensureOpen(room, studentId);
        DaySessionEntity session = dayOneSession(room.getId());
        return currentDeposit(session.getId(), studentId)
                .map(deposit -> toResponse(deposit, walletBalance(room.getId(), studentId), rules(room.getId()), false))
                .orElseGet(() -> emptyResponse(walletBalance(room.getId(), studentId), rules(room.getId())));
    }

    @Transactional(readOnly = true)
    public DepositHistoryResponse history(UUID studentId) {
        StudentEntity student = findActiveStudent(studentId);
        RoomEntity room = findRoom(student.getRoomId());
        ensureOpen(room, studentId);
        DaySessionEntity session = dayOneSession(room.getId());
        List<DepositHistoryItemResponse> deposits = depositRepository
                .findAllBySessionIdAndStudentIdAndStatusOrderByConfirmedAtDesc(
                        session.getId(), studentId, "CONFIRMED"
                )
                .stream()
                .map(deposit -> new DepositHistoryItemResponse(
                        deposit.getId(),
                        deposit.getPrincipal(),
                        deposit.getInterestRateBps(),
                        deposit.getExpectedInterest(),
                        deposit.getPrincipal() + deposit.getExpectedInterest(),
                        deposit.getConfirmedAt()
                ))
                .toList();
        return new DepositHistoryResponse(deposits);
    }

    @Transactional
    public DepositResponse saveDraft(UUID studentId, SaveDraftRequest request) {
        StudentEntity snapshot = findActiveStudent(studentId);
        RoomEntity room = lockRoom(snapshot.getRoomId());
        RoomRulesEntity rules = rules(room.getId());
        validatePrincipal(request.principal(), rules);
        StudentEntity student = lockActiveStudent(studentId);
        ensureOpen(room, studentId);
        AccountEntity wallet = walletForUpdate(room.getId(), studentId);
        if (wallet.getBalance() < request.principal()) {
            throw insufficientBalance();
        }
        DaySessionEntity session = dayOneSession(room.getId());
        long interest = expectedInterest(request.principal(), rules.getDepositInterestBps());
        DepositEntity deposit = depositRepository.findDraftForUpdate(session.getId(), studentId)
                .map(existing -> {
                    existing.changeDraft(request.principal(), rules.getDepositInterestBps(), interest);
                    return existing;
                })
                .orElseGet(() -> DepositEntity.draft(
                        room.getId(),
                        session.getId(),
                        studentId,
                        request.principal(),
                        rules.getDepositInterestBps(),
                        interest
                ));
        depositRepository.save(deposit);
        room.markEconomicStateChanged();
        return toResponse(deposit, wallet.getBalance(), rules, false);
    }

    @Transactional
    public DepositResponse cancelDraft(UUID studentId) {
        StudentEntity snapshot = findActiveStudent(studentId);
        RoomEntity room = lockRoom(snapshot.getRoomId());
        StudentEntity student = lockActiveStudent(studentId);
        ensureOpen(room, studentId);
        DaySessionEntity session = dayOneSession(room.getId());
        DepositEntity deposit = depositRepository.findDraftForUpdate(session.getId(), studentId)
                .orElseThrow(this::draftNotFound);
        deposit.cancelDraft();
        room.markEconomicStateChanged();
        return toResponse(deposit, walletBalance(room.getId(), studentId), rules(room.getId()), false);
    }

    @Transactional
    public DepositResponse confirm(UUID studentId, String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        StudentEntity snapshot = findActiveStudent(studentId);
        RoomEntity room = lockRoom(snapshot.getRoomId());
        StudentEntity student = lockActiveStudent(studentId);
        ensureOpen(room, studentId);

        var existingTransaction = transactionRepository.findByIdempotencyKey(normalizedKey);
        if (existingTransaction.isPresent()) {
            FinancialTransactionEntity transaction = existingTransaction.get();
            if (!"DEPOSIT_CONFIRM".equals(transaction.getTransactionType())
                    || !studentId.equals(transaction.getStudentId())) {
                throw idempotencyKeyReused();
            }
            DepositEntity deposit = depositRepository.findByTransactionId(transaction.getId())
                    .orElseThrow(this::idempotencyKeyReused);
            return toResponse(deposit, walletBalance(room.getId(), studentId), rules(room.getId()), true);
        }

        DaySessionEntity session = dayOneSession(room.getId());
        DepositEntity deposit = depositRepository.findDraftForUpdate(session.getId(), studentId)
                .orElseThrow(this::draftNotFound);
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
        return toResponse(deposit, wallet.getBalance(), rules(room.getId()), false);
    }

    private DepositResponse toResponse(DepositEntity deposit, long walletBalance, RoomRulesEntity rules, boolean replayed) {
        return new DepositResponse(
                deposit.getId(),
                deposit.getStatus(),
                deposit.getPrincipal(),
                deposit.getInterestRateBps(),
                deposit.getExpectedInterest(),
                deposit.getPrincipal() + deposit.getExpectedInterest(),
                walletBalance,
                rules.getDepositMinAmount(),
                rules.getDepositUnit(),
                deposit.getUpdatedAt(),
                deposit.getConfirmedAt(),
                replayed
        );
    }

    private DepositResponse emptyResponse(long walletBalance, RoomRulesEntity rules) {
        return new DepositResponse(
                null, "NONE", 0, rules.getDepositInterestBps(), 0, 0,
                walletBalance, rules.getDepositMinAmount(), rules.getDepositUnit(), null, null, false
        );
    }

    private java.util.Optional<DepositEntity> currentDeposit(UUID sessionId, UUID studentId) {
        return depositRepository
                .findFirstBySessionIdAndStudentIdAndStatusOrderByUpdatedAtDesc(
                        sessionId, studentId, "DRAFT"
                )
                .or(() -> depositRepository
                        .findFirstBySessionIdAndStudentIdAndStatusOrderByUpdatedAtDesc(
                                sessionId, studentId, "CONFIRMED"
                        ));
    }

    private long expectedInterest(long principal, int interestRateBps) {
        try {
            return Math.addExact(Math.multiplyExact(principal, interestRateBps), 5_000) / 10_000;
        } catch (ArithmeticException exception) {
            throw invalidAmount();
        }
    }

    private void validatePrincipal(long principal, RoomRulesEntity rules) {
        if (principal < rules.getDepositMinAmount() || principal % rules.getDepositUnit() != 0) {
            throw invalidAmount();
        }
    }

    private RoomRulesEntity rules(UUID roomId) {
        return rulesRepository.findByRoomId(roomId).orElseThrow(() -> new ApiException(
                HttpStatus.CONFLICT, "ROOM_RULES_NOT_FOUND", "수업 경제 설정을 찾을 수 없습니다."
        ));
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

    private void ensureOpen(RoomEntity room, UUID studentId) {
        if (!OPEN_STAGE.equals(room.getCurrentStage())
                && !studentFlowService.canUseStage(studentId, OPEN_STAGE)) {
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
