package com.b201.ecovillage.finance.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.finance.domain.DepositAccount;
import com.b201.ecovillage.finance.domain.FinancialProduct;
import com.b201.ecovillage.finance.domain.FinancialProductVersion;
import com.b201.ecovillage.finance.repository.DepositAccountRepository;
import com.b201.ecovillage.finance.repository.FinancialProductRepository;
import com.b201.ecovillage.finance.repository.FinancialProductVersionRepository;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStatus;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
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
import com.b201.ecovillage.ledger.domain.PolicySnapshot;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ledger.repository.PolicySnapshotRepository;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;

@Service
public class DepositSubscriptionService {

    private static final String ROUTE_KEY =
        "/classrooms/{classroom_id}/deposit-accounts";
    private static final String DEPOSIT_LIABILITY_KEY = "DEPOSIT_LIABILITY";

    private final ClassroomAuthorizationService authorizationService;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRepository studentRepository;
    private final ClassroomModuleRepository moduleRepository;
    private final FinancialProductRepository productRepository;
    private final FinancialProductVersionRepository versionRepository;
    private final DepositAccountRepository accountRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final PolicySnapshotRepository snapshotRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final AtomicLedgerTransactionService transactionService;
    private final RealtimeEventPublisher eventPublisher;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public DepositSubscriptionService(
        ClassroomAuthorizationService authorizationService,
        ClassroomRepository classroomRepository,
        ClassroomStudentRepository studentRepository,
        ClassroomModuleRepository moduleRepository,
        FinancialProductRepository productRepository,
        FinancialProductVersionRepository versionRepository,
        DepositAccountRepository accountRepository,
        LedgerAccountRepository ledgerAccountRepository,
        PolicySnapshotRepository snapshotRepository,
        IdempotencyRecordRepository idempotencyRepository,
        AtomicLedgerTransactionService transactionService,
        RealtimeEventPublisher eventPublisher,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.classroomRepository = classroomRepository;
        this.studentRepository = studentRepository;
        this.moduleRepository = moduleRepository;
        this.productRepository = productRepository;
        this.versionRepository = versionRepository;
        this.accountRepository = accountRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.snapshotRepository = snapshotRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.transactionService = transactionService;
        this.eventPublisher = eventPublisher;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public DepositAccountResult subscribe(DepositSubscriptionCommand command) {
        validate(command);
        UUID studentId = studentId(command.studentSubject());
        authorizationService.authorize(
            command.studentSubject(),
            command.classroomId(),
            ClassroomPermission.USE_FINANCIAL_PRODUCTS,
            studentId
        );
        Classroom classroom = requireActiveClassroom(command.classroomId());
        if (
            command.expectedStateVersion() != null
                && command.expectedStateVersion() != classroom.getStateVersion()
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The classroom state has changed."
            );
        }
        ClassroomStudent student = studentRepository.findById(studentId)
            .filter(candidate ->
                candidate.getClassroomId().equals(command.classroomId())
                    && candidate.getStatus() == ClassroomStudentStatus.ACTIVE
            )
            .orElseThrow(() -> operation(
                HttpStatus.FORBIDDEN,
                "RESOURCE_NOT_OWNED",
                "The student is not active in this classroom."
            ));
        String requestHash = requestHash(command);
        DepositAccountResult replay = replay(
            command.classroomId(),
            studentId,
            command.requestId(),
            requestHash
        );
        if (replay != null) {
            return replay;
        }
        if (!moduleRepository.existsByClassroomIdAndModuleCodeAndStatus(
            command.classroomId(),
            "DEPOSIT",
            "ACTIVE"
        )) {
            throw operation(
                HttpStatus.CONFLICT,
                "MODULE_NOT_ACTIVE",
                "The deposit module is not active."
            );
        }

        FinancialProduct product = productRepository
            .findByClassroomIdAndId(command.classroomId(), command.productId())
            .filter(FinancialProduct::isPublished)
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "PRODUCT_NOT_ACTIVE",
                "The deposit product is not published."
            ));
        FinancialProductVersion version = versionRepository
            .findTopByClassroomIdAndFinancialProductIdAndStatusOrderByVersionDesc(
                command.classroomId(),
                product.getId(),
                "PUBLISHED"
            )
            .filter(FinancialProductVersion::isPublishedDeposit)
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "PRODUCT_NOT_ACTIVE",
                "The product has no published deposit terms."
            ));
        if (version.getVersion() != command.expectedProductVersion()) {
            throw operation(
                HttpStatus.CONFLICT,
                "PRODUCT_VERSION_CONFLICT",
                "The deposit product terms have changed."
            );
        }
        if (!version.accepts(command.principal())) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "AMOUNT_OUT_OF_RANGE",
                "The principal is outside the product amount range."
            );
        }

        Instant now = clock.instant();
        long interest = interest(command.principal(), version.getInterestRateBps());
        Instant maturesAt = now.plus(Duration.ofDays(version.getTermUnits()));
        PolicySnapshot snapshot = snapshot(
            command,
            version,
            interest,
            maturesAt,
            now
        );
        LedgerAccount wallet = wallet(command.classroomId(), student.getId(), now);
        LedgerAccount liability = liability(command.classroomId(), now);
        UUID groupId = uuidGenerator.generate();
        DepositAccount account = DepositAccount.open(
            uuidGenerator.generate(),
            command.classroomId(),
            student.getId(),
            version.getId(),
            command.principal(),
            interest,
            now,
            maturesAt,
            groupId
        );
        LedgerDomainMutation openAccount = new LedgerDomainMutation() {
            @Override
            public void validate() {
                if (!version.accepts(command.principal())) {
                    throw operation(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "AMOUNT_OUT_OF_RANGE",
                        "The product amount range changed."
                    );
                }
            }

            @Override
            public void apply() {
                accountRepository.save(account);
            }
        };
        transactionService.execute(
            groupId,
            LedgerTransactionCommand.original(
                command.classroomId(),
                "DEPOSIT_OPEN",
                LedgerActorType.STUDENT,
                student.getId(),
                snapshot.getId(),
                ROUTE_KEY,
                command.requestId().toString(),
                requestHash,
                command.correlationId().toString(),
                "예금 가입",
                List.of(
                    new LedgerTransactionPosting(wallet.getId(), -command.principal()),
                    new LedgerTransactionPosting(liability.getId(), command.principal())
                ),
                now
            ),
            List.of(openAccount)
        );
        accountRepository.flush();
        eventPublisher.publish(
            command.classroomId(),
            "STUDENT_PRIVATE",
            student.getId(),
            "deposit.account.opened",
            "DEPOSIT_ACCOUNT",
            account.getId(),
            account.getStateVersion(),
            Map.of(
                "account_id", account.getId().toString(),
                "status", account.getStatus().name()
            ),
            false
        );
        return result(account);
    }

    private DepositAccountResult replay(
        UUID classroomId,
        UUID studentId,
        UUID requestId,
        String requestHash
    ) {
        IdempotencyRecord record = idempotencyRepository
            .findForReplay(classroomId, studentId, ROUTE_KEY, requestId.toString())
            .orElse(null);
        if (record == null) {
            return null;
        }
        if (!record.hasRequestHash(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        DepositAccount account = accountRepository
            .findByClassroomIdAndOpeningTransactionGroupId(
                classroomId,
                record.getTransactionGroupId()
            )
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_RESULT_MISSING",
                "The previous deposit result could not be found."
            ));
        return result(account);
    }

    private PolicySnapshot snapshot(
        DepositSubscriptionCommand command,
        FinancialProductVersion version,
        long interest,
        Instant maturesAt,
        Instant now
    ) {
        String hash = sha256(
            command.classroomId() + "|" + version.getId() + "|" + command.principal()
                + "|" + interest + "|" + maturesAt
        );
        return snapshotRepository
            .findByClassroomIdAndSnapshotHash(command.classroomId(), hash)
            .orElseGet(() -> snapshotRepository.save(
                PolicySnapshot.capture(
                    uuidGenerator.generate(),
                    command.classroomId(),
                    hash,
                    "1",
                    Map.of(
                        "financial_product_version_id", version.getId(),
                        "product_version", version.getVersion(),
                        "term_unit", "DAY",
                        "term_units", version.getTermUnits(),
                        "interest_method", "SIMPLE_FULL_TERM",
                        "interest_rate_bps", version.getInterestRateBps(),
                        "principal_amount", command.principal(),
                        "expected_interest_amount", interest,
                        "matures_at", maturesAt.toString()
                    ),
                    "HALF_UP",
                    now
                )
            ));
    }

    private LedgerAccount wallet(UUID classroomId, UUID studentId, Instant now) {
        return ledgerAccountRepository
            .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                classroomId,
                studentId,
                LedgerAccountType.STUDENT_WALLET,
                LedgerAccountStatus.ACTIVE
            )
            .orElseGet(() -> ledgerAccountRepository.save(
                LedgerAccount.studentWallet(
                    uuidGenerator.generate(),
                    classroomId,
                    studentId,
                    now
                )
            ));
    }

    private LedgerAccount liability(UUID classroomId, Instant now) {
        return ledgerAccountRepository
            .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                classroomId,
                DEPOSIT_LIABILITY_KEY,
                LedgerAccountType.DEPOSIT_LIABILITY,
                LedgerAccountStatus.ACTIVE
            )
            .orElseGet(() -> ledgerAccountRepository.save(
                LedgerAccount.systemAccount(
                    uuidGenerator.generate(),
                    classroomId,
                    DEPOSIT_LIABILITY_KEY,
                    LedgerAccountType.DEPOSIT_LIABILITY,
                    now
                )
            ));
    }

    private Classroom requireActiveClassroom(UUID classroomId) {
        Classroom classroom = classroomRepository.findById(classroomId)
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The classroom could not be found."
            ));
        if (classroom.getStatus() != ClassroomStatus.ACTIVE) {
            throw operation(
                HttpStatus.CONFLICT,
                "CLASS_NOT_ACTIVE",
                "Deposits are available only in an active classroom."
            );
        }
        return classroom;
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
                "The expected deposit interest exceeds the supported range."
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

    private static void validate(DepositSubscriptionCommand command) {
        if (
            command == null
                || command.classroomId() == null
                || command.requestId() == null
                || command.correlationId() == null
                || command.productId() == null
                || command.principal() <= 0
                || command.expectedProductVersion() < 1
        ) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Check the deposit subscription values."
            );
        }
    }

    private static String requestHash(DepositSubscriptionCommand command) {
        return sha256(
            command.classroomId() + "|" + command.productId() + "|"
                + command.principal() + "|" + command.expectedProductVersion()
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
            throw new IllegalStateException("Could not hash the deposit request.", exception);
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
}
