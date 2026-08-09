package com.b201.ecovillage.ledger.application;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.TransactionGroup;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ledger.repository.LedgerEntryRepository;
import com.b201.ecovillage.ledger.repository.TransactionGroupRepository;

@Service
public class StudentWalletQueryService {

    private final ClassroomAuthorizationService authorizationService;
    private final LedgerAccountRepository accountRepository;
    private final LedgerEntryRepository entryRepository;
    private final TransactionGroupRepository transactionGroupRepository;

    public StudentWalletQueryService(
        ClassroomAuthorizationService authorizationService,
        LedgerAccountRepository accountRepository,
        LedgerEntryRepository entryRepository,
        TransactionGroupRepository transactionGroupRepository
    ) {
        this.authorizationService = authorizationService;
        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
        this.transactionGroupRepository = transactionGroupRepository;
    }

    @Transactional(readOnly = true)
    public StudentWalletView findMine(String subject, UUID classroomId) {
        UUID studentId = studentId(subject);
        authorizationService.authorize(
            subject,
            classroomId,
            ClassroomPermission.READ_OWN_STUDENT_DATA,
            studentId
        );
        LedgerAccount wallet = accountRepository
            .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                classroomId,
                studentId,
                LedgerAccountType.STUDENT_WALLET,
                LedgerAccountStatus.ACTIVE
            )
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "학생 지갑을 찾을 수 없습니다."
            ));

        var entries = entryRepository
            .findTop20ByLedgerAccountIdOrderByAccountSequenceDesc(wallet.getId());
        Map<UUID, TransactionGroup> groups = transactionGroupRepository
            .findAllByIdIn(entries.stream().map(entry -> entry.getTransactionGroupId()).toList())
            .stream()
            .collect(Collectors.toMap(TransactionGroup::getId, Function.identity()));

        var transactions = entries.stream().map(entry -> {
            TransactionGroup group = groups.get(entry.getTransactionGroupId());
            String type = group == null ? "거래" : group.getTransactionType();
            String label = group != null && group.getReason() != null && !group.getReason().isBlank()
                ? group.getReason()
                : label(type);
            return new StudentWalletView.RecentTransaction(
                entry.getTransactionGroupId(),
                label,
                group == null ? "COMMITTED" : group.getStatus().name(),
                1,
                group == null ? 0L : group.getStateVersion(),
                entry.getAmount(),
                group != null && group.getCommittedAt() != null
                    ? group.getCommittedAt()
                    : entry.getCreatedAt()
            );
        }).toList();
        return new StudentWalletView(wallet.getCurrentBalance(), transactions);
    }

    private static String label(String transactionType) {
        return switch (transactionType) {
            case "SALARY_PAYOUT" -> "급여 지급";
            case "STORE_PURCHASE" -> "상점 구매";
            case "PERSONAL_TRADE" -> "개인 거래";
            case "DEPOSIT_OPEN" -> "예금 가입";
            case "DEPOSIT_MATURITY" -> "예금 만기 지급";
            case "DEPOSIT_EARLY_TERMINATION" -> "예금 중도 해지";
            case "SAVINGS_OPEN" -> "적금 가입";
            case "SAVINGS_INSTALLMENT" -> "적금 납입";
            case "SAVINGS_MATURITY" -> "적금 만기 지급";
            case "LOAN_DISBURSEMENT" -> "대출 실행";
            case "LOAN_REPAYMENT" -> "대출 상환";
            case "LOAN_PREPAYMENT" -> "대출 중도 상환";
            case "QUIZ_REWARD" -> "퀴즈 보상";
            case "INVESTMENT_BUY" -> "투자 매수";
            case "INVESTMENT_SELL" -> "투자 매도";
            case "INVESTMENT_SETTLEMENT" -> "투자 정산";
            case "SUBSCRIPTION_DEPOSIT" -> "청약통장 납입";
            case "SUBSCRIPTION_PROPERTY_SALE" -> "청약 당첨 자리 구매";
            case "PROPERTY_SALE" -> "부동산 자리 구매";
            case "PROPERTY_RENT_DEPOSIT" -> "월세 보증금 납부";
            case "PROPERTY_JEONSE_DEPOSIT" -> "전세 보증금 납부";
            case "PROPERTY_RENT_PAYMENT" -> "임대료 납부";
            case "PROPERTY_DEPOSIT_REFUND" -> "보증금 반환";
            case "PUBLIC_FUND_SPENDING" -> "공동기금 사용";
            case "ECONOMIC_EVENT_REWARD_ISSUANCE" -> "경제 이벤트 보상";
            case "ECONOMIC_EVENT_PUBLIC_FUND_GRANT" -> "공동기금 지원";
            case "RECOVERY_AMOUNT_CORRECTION" -> "금액 정정";
            case "RECOVERY_ITEM_CORRECTION" -> "항목 정정";
            case "RECOVERY_QUIZ_REGRADING" -> "퀴즈 재채점 정정";
            default -> "거래";
        };
    }

    private static UUID studentId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (RuntimeException exception) {
            throw operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "학생 인증 정보를 확인할 수 없습니다."
            );
        }
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
