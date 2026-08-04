package com.b201.ecovillage.ledger.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.LedgerActorType;
import com.b201.ecovillage.ledger.domain.TransactionBusinessRef;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ledger.repository.TransactionBusinessRefRepository;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;

/**
 * 승인된 경제 이벤트가 원장에 지급을 남기는 진입점이다.
 *
 * <p>AI 승인 서비스는 이 진입점만 호출하고 원장 계정이나 거래를 직접 만들지 않는다.
 * 집행 주체는 SYSTEM이며 승인한 교사는 감사 기록에만 남는다.
 *
 * <p>보상과 공동기금 교부는 모두 발행 계좌에서 나간다. 공동기금 잔액 부족으로 수업이 막히지
 * 않게 하려는 결정이며, 공동기금은 공공 지출·투표 영역에서만 차감한다.
 *
 * <p>재시도로 같은 초안이 두 번 지급되지 않게 {@code transaction_business_refs}에
 * 초안 id를 남기고, 이미 있으면 기존 거래를 그대로 돌려준다.
 */
@Service
public class EconomicEventPayoutService {

    public static final String REWARD_TRANSACTION_TYPE = "ECONOMIC_EVENT_REWARD_ISSUANCE";
    public static final String PUBLIC_FUND_TRANSACTION_TYPE = "ECONOMIC_EVENT_PUBLIC_FUND_GRANT";
    static final String BUSINESS_REF_TYPE = "ECONOMIC_EVENT_DRAFT";

    private static final String MONEY_ISSUANCE_KEY = "MONEY_ISSUANCE";
    private static final String CLASSROOM_FUND_KEY = "CLASSROOM_FUND";
    private static final String REWARD_ROUTE =
        "/classrooms/{classroom_id}/ai/economic-events/{event_id}/reward-issuance";
    private static final String PUBLIC_FUND_ROUTE =
        "/classrooms/{classroom_id}/ai/economic-events/{event_id}/public-fund-grant";

    /**
     * 지급 한도다. 규칙 문서에 수치가 정의되지 않아 운영 사고를 막는 상한으로만 둔다.
     * 정식 수치가 정해지면 정책 값으로 옮긴다.
     */
    static final long MAX_REWARD_PER_STUDENT = 100_000L;
    static final long MAX_TOTAL_PER_EVENT = 5_000_000L;

    private final AtomicLedgerTransactionService ledgerTransactionService;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final TransactionBusinessRefRepository businessRefRepository;
    private final ClassroomStudentRepository studentRepository;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public EconomicEventPayoutService(
        AtomicLedgerTransactionService ledgerTransactionService,
        LedgerAccountRepository ledgerAccountRepository,
        TransactionBusinessRefRepository businessRefRepository,
        ClassroomStudentRepository studentRepository,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.ledgerTransactionService = ledgerTransactionService;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.businessRefRepository = businessRefRepository;
        this.studentRepository = studentRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    /**
     * 활동을 함께한 학생 전원에게 같은 금액을 발행 계좌에서 지급한다.
     * 특정 학생을 지목하지 않는다는 경제 이벤트 안전 경계를 구조로 지킨다.
     */
    @Transactional
    public EconomicEventPayoutResult issueReward(EconomicEventPayoutCommand command) {
        requirePositive(command.amount());
        if (command.amount() > MAX_REWARD_PER_STUDENT) {
            throw new LedgerTransactionValidationException(
                "PAYOUT_LIMIT_EXCEEDED",
                "reward per student exceeds " + MAX_REWARD_PER_STUDENT
            );
        }
        Optional<EconomicEventPayoutResult> replay = replayed(command);
        if (replay.isPresent()) {
            return replay.get();
        }

        List<ClassroomStudent> students = studentRepository
            .findAllByClassroomIdAndStatusOrderByRosterNumber(
                command.classroomId(),
                ClassroomStudentStatus.ACTIVE
            );
        if (students.isEmpty()) {
            throw new LedgerTransactionValidationException(
                "ACTIVE_STUDENTS_ZERO",
                "the classroom has no active student to reward"
            );
        }
        long total = Math.multiplyExact(command.amount(), (long) students.size());
        requireWithinEventLimit(total);

        Instant now = clock.instant();
        LedgerAccount issuance = issuanceAccount(command.classroomId(), now);
        List<LedgerTransactionPosting> postings = new ArrayList<>();
        postings.add(new LedgerTransactionPosting(issuance.getId(), -total));
        students.stream()
            .sorted(Comparator.comparing(ClassroomStudent::getId))
            .forEach(student -> postings.add(new LedgerTransactionPosting(
                studentWallet(command.classroomId(), student.getId(), now).getId(),
                command.amount()
            )));

        return commit(
            command,
            REWARD_TRANSACTION_TYPE,
            REWARD_ROUTE,
            postings,
            total,
            students.size(),
            now
        );
    }

    /**
     * 발행 계좌에서 학급 공동기금으로 교부한다. 공동기금 지출은 투표 영역이 계속 담당한다.
     */
    @Transactional
    public EconomicEventPayoutResult grantPublicFund(EconomicEventPayoutCommand command) {
        requirePositive(command.amount());
        requireWithinEventLimit(command.amount());
        Optional<EconomicEventPayoutResult> replay = replayed(command);
        if (replay.isPresent()) {
            return replay.get();
        }

        Instant now = clock.instant();
        LedgerAccount issuance = issuanceAccount(command.classroomId(), now);
        LedgerAccount fund = fundAccount(command.classroomId(), now);
        List<LedgerTransactionPosting> postings = List.of(
            new LedgerTransactionPosting(issuance.getId(), -command.amount()),
            new LedgerTransactionPosting(fund.getId(), command.amount())
        );

        return commit(
            command,
            PUBLIC_FUND_TRANSACTION_TYPE,
            PUBLIC_FUND_ROUTE,
            postings,
            command.amount(),
            1,
            now
        );
    }

    private EconomicEventPayoutResult commit(
        EconomicEventPayoutCommand command,
        String transactionType,
        String route,
        List<LedgerTransactionPosting> postings,
        long total,
        int recipientCount,
        Instant now
    ) {
        UUID groupId = uuidGenerator.generate();
        LedgerTransactionResult ledgerResult = ledgerTransactionService.execute(
            groupId,
            LedgerTransactionCommand.original(
                command.classroomId(),
                transactionType,
                // 실제 집행 주체는 시스템이다. 승인한 교사는 감사 기록에만 남는다.
                LedgerActorType.SYSTEM,
                command.classroomId(),
                null,
                route,
                command.economicEventDraftId().toString(),
                command.economicEventDraftId() + "|" + transactionType + "|" + total,
                command.correlationId().toString(),
                command.reason(),
                postings,
                now
            ),
            List.of()
        );
        businessRefRepository.save(TransactionBusinessRef.create(
            uuidGenerator.generate(),
            command.classroomId(),
            ledgerResult.transactionGroupId(),
            BUSINESS_REF_TYPE,
            command.economicEventDraftId(),
            now
        ));
        return new EconomicEventPayoutResult(
            ledgerResult.transactionGroupId(),
            total,
            recipientCount,
            ledgerResult.replayed()
        );
    }

    /**
     * 같은 초안으로 이미 지급했으면 그 거래를 그대로 돌려준다. 재시도가 두 번 지급하지 않는다.
     */
    private Optional<EconomicEventPayoutResult> replayed(EconomicEventPayoutCommand command) {
        return businessRefRepository
            .findTopByClassroomIdAndRefTypeAndRefIdOrderByCreatedAtDesc(
                command.classroomId(),
                BUSINESS_REF_TYPE,
                command.economicEventDraftId()
            )
            .map(reference -> new EconomicEventPayoutResult(
                reference.getTransactionGroupId(),
                0L,
                0,
                true
            ));
    }

    private static void requirePositive(long amount) {
        if (amount <= 0L) {
            throw new LedgerTransactionValidationException(
                "VALIDATION_ERROR",
                "economic event payout amount must be positive"
            );
        }
    }

    private static void requireWithinEventLimit(long total) {
        if (total > MAX_TOTAL_PER_EVENT) {
            throw new LedgerTransactionValidationException(
                "PAYOUT_LIMIT_EXCEEDED",
                "economic event payout total exceeds " + MAX_TOTAL_PER_EVENT
            );
        }
    }

    private LedgerAccount issuanceAccount(UUID classroomId, Instant now) {
        return systemAccount(
            classroomId,
            MONEY_ISSUANCE_KEY,
            LedgerAccountType.MONEY_ISSUANCE,
            now
        );
    }

    private LedgerAccount fundAccount(UUID classroomId, Instant now) {
        return systemAccount(
            classroomId,
            CLASSROOM_FUND_KEY,
            LedgerAccountType.CLASSROOM_FUND,
            now
        );
    }

    private LedgerAccount systemAccount(
        UUID classroomId,
        String ownerKey,
        LedgerAccountType accountType,
        Instant now
    ) {
        return ledgerAccountRepository
            .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                classroomId,
                ownerKey,
                accountType,
                LedgerAccountStatus.ACTIVE
            )
            .orElseGet(() -> ledgerAccountRepository.save(LedgerAccount.systemAccount(
                uuidGenerator.generate(),
                classroomId,
                ownerKey,
                accountType,
                now
            )));
    }

    private LedgerAccount studentWallet(UUID classroomId, UUID studentId, Instant now) {
        return ledgerAccountRepository
            .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                classroomId,
                studentId,
                LedgerAccountType.STUDENT_WALLET,
                LedgerAccountStatus.ACTIVE
            )
            .orElseGet(() -> ledgerAccountRepository.save(LedgerAccount.studentWallet(
                uuidGenerator.generate(),
                classroomId,
                studentId,
                now
            )));
    }
}
