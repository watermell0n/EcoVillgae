package com.b201.ecovillage.finance.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.finance.domain.DepositAccount;
import com.b201.ecovillage.finance.domain.DepositAccountStatus;
import com.b201.ecovillage.finance.domain.FinancialProductVersion;
import com.b201.ecovillage.finance.domain.LoanCreditPolicy;
import com.b201.ecovillage.finance.repository.DepositAccountRepository;
import com.b201.ecovillage.finance.repository.FinancialProductVersionRepository;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.ledger.application.AtomicLedgerTransactionService;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.application.LedgerDomainMutation;
import com.b201.ecovillage.ledger.application.LedgerTransactionCommand;
import com.b201.ecovillage.ledger.application.LedgerTransactionPosting;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.LedgerActorType;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;

@Service
public class DepositTerminationService {

    private static final String ROUTE_KEY =
        "/classrooms/{classroom_id}/deposit-accounts/{resource_id}/commands";
    private static final String DEPOSIT_LIABILITY_KEY = "DEPOSIT_LIABILITY";
    private static final String MONEY_ISSUANCE_KEY = "MONEY_ISSUANCE";
    private static final int EARLY_TERMINATION_CREDIT_PENALTY = -10;

    private final ClassroomAuthorizationService authorizationService;
    private final DepositAccountRepository accountRepository;
    private final FinancialProductVersionRepository versionRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final AtomicLedgerTransactionService transactionService;
    private final RealtimeEventPublisher eventPublisher;
    private final JdbcTemplate jdbc;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public DepositTerminationService(
        ClassroomAuthorizationService authorizationService,
        DepositAccountRepository accountRepository,
        FinancialProductVersionRepository versionRepository,
        LedgerAccountRepository ledgerAccountRepository,
        IdempotencyRecordRepository idempotencyRepository,
        AtomicLedgerTransactionService transactionService,
        RealtimeEventPublisher eventPublisher,
        JdbcTemplate jdbc,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.accountRepository = accountRepository;
        this.versionRepository = versionRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.transactionService = transactionService;
        this.eventPublisher = eventPublisher;
        this.jdbc = jdbc;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public DepositAccountResult terminate(DepositTerminationCommand command) {
        validate(command);
        UUID studentId = studentId(command.studentSubject());
        authorizationService.authorize(
            command.studentSubject(),
            command.classroomId(),
            ClassroomPermission.USE_FINANCIAL_PRODUCTS,
            studentId
        );
        String requestHash = requestHash(command);
        DepositAccountResult replay = replay(command, studentId, requestHash);
        if (replay != null) {
            return replay;
        }

        DepositAccount account = accountRepository
            .findForUpdate(command.classroomId(), command.accountId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The deposit account could not be found."
            ));
        replay = replay(command, studentId, requestHash);
        if (replay != null) {
            return replay;
        }
        if (!account.getClassroomStudentId().equals(studentId)) {
            throw operation(
                HttpStatus.FORBIDDEN,
                "RESOURCE_NOT_OWNED",
                "The deposit account belongs to another student."
            );
        }
        if (account.getStatus() != DepositAccountStatus.ACTIVE) {
            throw operation(
                HttpStatus.CONFLICT,
                "INVALID_STATE_TRANSITION",
                "Only an active deposit can be terminated."
            );
        }
        if (
            command.expectedStateVersion() != null
                && command.expectedStateVersion() != account.getStateVersion()
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The deposit account state has changed."
            );
        }

        Instant now = clock.instant();
        if (!now.isBefore(account.getMaturesAt())) {
            throw operation(
                HttpStatus.CONFLICT,
                "INVALID_STATE_TRANSITION",
                "The deposit has already reached maturity."
            );
        }
        FinancialProductVersion version = versionRepository
            .findById(account.getProductVersionId())
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "FINANCIAL_DATA_INCONSISTENT",
                "The deposit product version is missing."
            ));
        long interest = interest(
            account.getPrincipalAmount(),
            version.getEarlyTerminationRateBps()
        );
        long payout = payout(account.getPrincipalAmount(), interest);
        CreditSnapshot credit = creditForUpdate(command.classroomId(), studentId, now);
        LedgerAccount wallet = wallet(account, now);
        LedgerAccount liability = systemAccount(
            command.classroomId(),
            DEPOSIT_LIABILITY_KEY,
            LedgerAccountType.DEPOSIT_LIABILITY,
            now
        );
        LedgerAccount issuance = interest == 0
            ? null
            : systemAccount(
                command.classroomId(),
                MONEY_ISSUANCE_KEY,
                LedgerAccountType.MONEY_ISSUANCE,
                now
            );
        UUID groupId = uuidGenerator.generate();
        List<LedgerTransactionPosting> postings = new ArrayList<>();
        postings.add(new LedgerTransactionPosting(
            liability.getId(),
            -account.getPrincipalAmount()
        ));
        postings.add(new LedgerTransactionPosting(wallet.getId(), payout));
        if (issuance != null) {
            postings.add(new LedgerTransactionPosting(issuance.getId(), -interest));
        }
        LedgerDomainMutation termination = new LedgerDomainMutation() {
            @Override
            public void validate() {
                if (
                    account.getStatus() != DepositAccountStatus.ACTIVE
                        || !now.isBefore(account.getMaturesAt())
                ) {
                    throw operation(
                        HttpStatus.CONFLICT,
                        "INVALID_STATE_TRANSITION",
                        "The deposit is not eligible for early termination."
                    );
                }
                if (liability.getCurrentBalance() < account.getPrincipalAmount()) {
                    throw operation(
                        HttpStatus.CONFLICT,
                        "LIABILITY_BALANCE_MISMATCH",
                        "The deposit liability cannot cover the principal."
                    );
                }
            }

            @Override
            public void apply() {
                account.earlyTerminate(groupId, now);
                accountRepository.save(account);
                applyCreditPenalty(command.classroomId(), studentId, account.getId(), credit, now);
            }
        };
        transactionService.execute(
            groupId,
            LedgerTransactionCommand.original(
                command.classroomId(),
                "DEPOSIT_EARLY_TERMINATION",
                LedgerActorType.STUDENT,
                studentId,
                null,
                ROUTE_KEY,
                command.requestId().toString(),
                requestHash,
                command.correlationId().toString(),
                "예금 중도 해지",
                postings,
                now
            ),
            List.of(termination)
        );
        accountRepository.flush();
        eventPublisher.publish(
            command.classroomId(),
            "STUDENT_PRIVATE",
            studentId,
            "deposit.account.early_terminated",
            "DEPOSIT_ACCOUNT",
            account.getId(),
            account.getStateVersion(),
            java.util.Map.of(
                "account_id", account.getId().toString(),
                "status", account.getStatus().name()
            ),
            false
        );
        return result(account);
    }

    private CreditSnapshot creditForUpdate(UUID classroomId, UUID studentId, Instant now) {
        CreditSnapshot existing = jdbc.query(
            "select score,level from credit_profiles where classroom_id=? and classroom_student_id=? for update",
            rs -> rs.next() ? new CreditSnapshot(rs.getInt(1), rs.getString(2)) : null,
            classroomId,
            studentId
        );
        if (existing != null) {
            return existing;
        }
        jdbc.update(
            "insert into credit_profiles(classroom_student_id,classroom_id,level,score,evaluated_at,created_at,updated_at) values(?,?,'NORMAL',600,?,?,?)",
            studentId,
            classroomId,
            now,
            now,
            now
        );
        return new CreditSnapshot(600, "NORMAL");
    }

    private void applyCreditPenalty(
        UUID classroomId,
        UUID studentId,
        UUID accountId,
        CreditSnapshot before,
        Instant now
    ) {
        int afterScore = Math.max(0, before.score() + EARLY_TERMINATION_CREDIT_PENALTY);
        if (afterScore == before.score()) {
            return;
        }
        String afterLevel = LoanCreditPolicy.level(afterScore);
        jdbc.update(
            "update credit_profiles set score=?,level=?,state_version=state_version+1,evaluated_at=?,updated_at=? where classroom_id=? and classroom_student_id=?",
            afterScore,
            afterLevel,
            now,
            now,
            classroomId,
            studentId
        );
        jdbc.update(
            "insert into credit_events(id,classroom_id,classroom_student_id,event_type,score_delta,score_before,score_after,level_before,level_after,reason,related_type,related_id,occurred_at,created_at) values(?,?,?,?,?,?,?,?,?,?,'DEPOSIT_ACCOUNT',?,?,?)",
            uuidGenerator.generate(),
            classroomId,
            studentId,
            "DEPOSIT_EARLY_TERMINATION",
            afterScore - before.score(),
            before.score(),
            afterScore,
            before.level(),
            afterLevel,
            "예금을 만기 전에 해지해 신용점수가 내려갔어요.",
            accountId,
            now,
            now
        );
    }

    private DepositAccountResult replay(
        DepositTerminationCommand command,
        UUID studentId,
        String requestHash
    ) {
        IdempotencyRecord record = idempotencyRepository.findForReplay(
            command.classroomId(),
            studentId,
            ROUTE_KEY,
            command.requestId().toString()
        ).orElse(null);
        if (record == null) {
            return null;
        }
        if (!record.hasRequestHash(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        DepositAccount account = accountRepository
            .findByClassroomIdAndClosingTransactionGroupId(
                command.classroomId(),
                record.getTransactionGroupId()
            )
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_RESULT_MISSING",
                "The previous deposit termination result could not be found."
            ));
        return result(account);
    }

    private LedgerAccount wallet(DepositAccount account, Instant now) {
        return ledgerAccountRepository
            .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                account.getClassroomId(),
                account.getClassroomStudentId(),
                LedgerAccountType.STUDENT_WALLET,
                LedgerAccountStatus.ACTIVE
            )
            .orElseGet(() -> ledgerAccountRepository.save(
                LedgerAccount.studentWallet(
                    uuidGenerator.generate(),
                    account.getClassroomId(),
                    account.getClassroomStudentId(),
                    now
                )
            ));
    }

    private LedgerAccount systemAccount(
        UUID classroomId,
        String ownerKey,
        LedgerAccountType type,
        Instant now
    ) {
        return ledgerAccountRepository
            .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                classroomId,
                ownerKey,
                type,
                LedgerAccountStatus.ACTIVE
            )
            .orElseGet(() -> ledgerAccountRepository.save(
                LedgerAccount.systemAccount(
                    uuidGenerator.generate(),
                    classroomId,
                    ownerKey,
                    type,
                    now
                )
            ));
    }

    private static long interest(long principal, int rateBps) {
        try {
            return BigDecimal.valueOf(principal)
                .multiply(BigDecimal.valueOf(rateBps))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
                .longValueExact();
        } catch (ArithmeticException exception) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "AMOUNT_OVERFLOW",
                "The early termination interest exceeds the supported range."
            );
        }
    }

    private static long payout(long principal, long interest) {
        try {
            return Math.addExact(principal, interest);
        } catch (ArithmeticException exception) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "AMOUNT_OVERFLOW",
                "The early termination payout exceeds the supported range."
            );
        }
    }

    private static void validate(DepositTerminationCommand command) {
        if (
            command == null
                || command.classroomId() == null
                || command.accountId() == null
                || command.requestId() == null
                || command.correlationId() == null
                || !"EARLY_TERMINATE".equals(command.command())
        ) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Check the deposit termination values."
            );
        }
    }

    private static UUID studentId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (RuntimeException exception) {
            throw operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A valid student session is required."
            );
        }
    }

    private static String requestHash(DepositTerminationCommand command) {
        return sha256(
            command.classroomId() + "|" + command.accountId() + "|"
                + command.command() + "|" + command.expectedStateVersion()
        );
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash the deposit command.", exception);
        }
    }

    private static DepositAccountResult result(DepositAccount account) {
        return new DepositAccountResult(
            account.getId(),
            account.getStatus().name(),
            account.getPrincipalAmount(),
            account.getExpectedInterestAmount(),
            account.getMaturesAt(),
            account.getStateVersion()
        );
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }

    private record CreditSnapshot(int score, String level) { }
}
