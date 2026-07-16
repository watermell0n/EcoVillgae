package com.b201.bibim.feature.economy;

import java.util.List;
import java.util.UUID;

import com.b201.bibim.domain.deposit.DepositRepository;
import com.b201.bibim.domain.finance.AccountEntity;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.finance.FinancialEntryRepository;
import com.b201.bibim.domain.finance.FinancialTransactionRepository;
import com.b201.bibim.domain.investment.InvestmentOrderRepository;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.shop.PurchaseRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.economy.EconomyDto.OverviewResponse;
import com.b201.bibim.feature.economy.EconomyDto.TransactionHistoryResponse;
import com.b201.bibim.feature.economy.EconomyDto.TransactionItemResponse;
import com.b201.bibim.global.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EconomyService {

    private final StudentRepository studentRepository;
    private final RoomRepository roomRepository;
    private final DaySessionRepository sessionRepository;
    private final AccountRepository accountRepository;
    private final DepositRepository depositRepository;
    private final InvestmentOrderRepository orderRepository;
    private final PurchaseRepository purchaseRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final FinancialEntryRepository entryRepository;

    public EconomyService(
            StudentRepository studentRepository,
            RoomRepository roomRepository,
            DaySessionRepository sessionRepository,
            AccountRepository accountRepository,
            DepositRepository depositRepository,
            InvestmentOrderRepository orderRepository,
            PurchaseRepository purchaseRepository,
            FinancialTransactionRepository transactionRepository,
            FinancialEntryRepository entryRepository
    ) {
        this.studentRepository = studentRepository;
        this.roomRepository = roomRepository;
        this.sessionRepository = sessionRepository;
        this.accountRepository = accountRepository;
        this.depositRepository = depositRepository;
        this.orderRepository = orderRepository;
        this.purchaseRepository = purchaseRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
    }

    @Transactional(readOnly = true)
    public OverviewResponse overview(UUID studentId) {
        StudentEntity student = findActiveStudent(studentId);
        RoomEntity room = findOpenRoom(student.getRoomId());
        UUID sessionId = sessionRepository.findByRoomIdAndDayNumber(room.getId(), (short) 1)
                .orElseThrow(this::sessionNotFound)
                .getId();
        long wallet = balance(room.getId(), studentId, "STUDENT_WALLET");
        long deposit = balance(room.getId(), studentId, "STUDENT_DEPOSIT");
        long investment = balance(room.getId(), studentId, "STUDENT_INVESTMENT");
        String depositStatus = depositRepository.findBySessionIdAndStudentId(sessionId, studentId)
                .map(item -> item.getStatus())
                .orElse("NONE");
        String investmentStatus = orderRepository.findBySessionIdAndStudentId(sessionId, studentId)
                .map(item -> item.getStatus())
                .orElse("NONE");
        return new OverviewResponse(
                wallet,
                deposit,
                investment,
                purchaseRepository.countByStudentId(studentId),
                depositStatus,
                investmentStatus,
                Math.addExact(wallet, Math.addExact(deposit, investment))
        );
    }

    @Transactional(readOnly = true)
    public TransactionHistoryResponse transactions(UUID studentId) {
        StudentEntity student = findActiveStudent(studentId);
        RoomEntity room = findOpenRoom(student.getRoomId());
        AccountEntity wallet = accountRepository.findByRoomIdAndStudentIdAndAccountType(
                room.getId(), studentId, "STUDENT_WALLET"
        ).orElse(null);
        List<TransactionItemResponse> items = transactionRepository
                .findAllByStudentIdOrderByCreatedAtDesc(studentId)
                .stream()
                .limit(50)
                .map(transaction -> {
                    long walletAmount = wallet == null ? 0 : entryRepository
                            .findAllByTransactionIdOrderByEntryOrder(transaction.getId())
                            .stream()
                            .filter(entry -> wallet.getId().equals(entry.getAccountId()))
                            .mapToLong(entry -> entry.getAmount())
                            .sum();
                    return new TransactionItemResponse(
                            transaction.getId(),
                            transaction.getTransactionType(),
                            transaction.getStatus(),
                            transaction.getDescription(),
                            walletAmount,
                            transaction.getMetadata(),
                            transaction.getCreatedAt()
                    );
                })
                .toList();
        return new TransactionHistoryResponse(items);
    }

    private StudentEntity findActiveStudent(UUID studentId) {
        return studentRepository.findById(studentId)
                .filter(student -> "ACTIVE".equals(student.getStatus()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."
                ));
    }

    private RoomEntity findOpenRoom(UUID roomId) {
        RoomEntity room = roomRepository.findById(roomId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "수업 방을 찾을 수 없습니다."
        ));
        if (!"DAY1_FREE_ACTIVITY".equals(room.getCurrentStage())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "ECONOMY_ACTIVITY_CLOSED", "현재는 자유 경제활동 시간이 아닙니다."
            );
        }
        return room;
    }

    private long balance(UUID roomId, UUID studentId, String accountType) {
        return accountRepository.findByRoomIdAndStudentIdAndAccountType(
                roomId, studentId, accountType
        ).map(AccountEntity::getBalance).orElse(0L);
    }

    private ApiException sessionNotFound() {
        return new ApiException(HttpStatus.CONFLICT, "SESSION_NOT_FOUND", "1일차 수업을 찾을 수 없습니다.");
    }
}
