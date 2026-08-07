package com.b201.ecovillage.finance.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import com.b201.ecovillage.finance.domain.FinancialProduct;
import com.b201.ecovillage.finance.domain.FinancialProductType;
import com.b201.ecovillage.finance.domain.FinancialProductVersion;
import com.b201.ecovillage.finance.domain.SavingsAccount;
import com.b201.ecovillage.finance.domain.SavingsInstallment;
import com.b201.ecovillage.finance.repository.FinancialProductRepository;
import com.b201.ecovillage.finance.repository.FinancialProductVersionRepository;
import com.b201.ecovillage.finance.repository.SavingsAccountRepository;
import com.b201.ecovillage.finance.repository.SavingsInstallmentRepository;
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
public class SavingsSubscriptionService {
    private static final String ROUTE_KEY = "/classrooms/{classroom_id}/savings-accounts";
    private static final String LIABILITY_KEY = "SAVINGS_LIABILITY";

    private final ClassroomAuthorizationService authorizationService;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRepository studentRepository;
    private final ClassroomModuleRepository moduleRepository;
    private final FinancialProductRepository productRepository;
    private final FinancialProductVersionRepository versionRepository;
    private final SavingsAccountRepository accountRepository;
    private final SavingsInstallmentRepository installmentRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final PolicySnapshotRepository snapshotRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final AtomicLedgerTransactionService transactionService;
    private final RealtimeEventPublisher eventPublisher;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public SavingsSubscriptionService(ClassroomAuthorizationService authorizationService, ClassroomRepository classroomRepository,
        ClassroomStudentRepository studentRepository, ClassroomModuleRepository moduleRepository,
        FinancialProductRepository productRepository, FinancialProductVersionRepository versionRepository,
        SavingsAccountRepository accountRepository, SavingsInstallmentRepository installmentRepository,
        LedgerAccountRepository ledgerAccountRepository, PolicySnapshotRepository snapshotRepository,
        IdempotencyRecordRepository idempotencyRepository, AtomicLedgerTransactionService transactionService,
        RealtimeEventPublisher eventPublisher, UuidV7Generator uuidGenerator, Clock clock) {
        this.authorizationService = authorizationService; this.classroomRepository = classroomRepository;
        this.studentRepository = studentRepository; this.moduleRepository = moduleRepository;
        this.productRepository = productRepository; this.versionRepository = versionRepository;
        this.accountRepository = accountRepository; this.installmentRepository = installmentRepository;
        this.ledgerAccountRepository = ledgerAccountRepository; this.snapshotRepository = snapshotRepository;
        this.idempotencyRepository = idempotencyRepository; this.transactionService = transactionService;
        this.eventPublisher = eventPublisher; this.uuidGenerator = uuidGenerator; this.clock = clock;
    }

    @Transactional
    public FinancialResourceResult subscribe(SavingsSubscriptionCommand command) {
        validate(command);
        UUID studentId = studentId(command.studentSubject());
        authorizationService.authorize(command.studentSubject(), command.classroomId(), ClassroomPermission.USE_FINANCIAL_PRODUCTS, studentId);
        Classroom classroom = requireActiveClassroom(command.classroomId());
        if (command.expectedStateVersion() != null && command.expectedStateVersion() != classroom.getStateVersion()) {
            throw operation(HttpStatus.CONFLICT, "STATE_VERSION_CONFLICT", "The classroom state has changed.");
        }
        ClassroomStudent student = studentRepository.findById(studentId).filter(candidate ->
            candidate.getClassroomId().equals(command.classroomId()) && candidate.getStatus() == ClassroomStudentStatus.ACTIVE
        ).orElseThrow(() -> operation(HttpStatus.FORBIDDEN, "RESOURCE_NOT_OWNED", "The student is not active in this classroom."));
        String requestHash = hash(command.classroomId() + "|" + command.productId() + "|" + command.installmentAmount() + "|" + command.expectedProductVersion());
        FinancialResourceResult replay = replay(command.classroomId(), studentId, command.requestId(), requestHash);
        if (replay != null) return replay;
        if (!moduleRepository.existsByClassroomIdAndModuleCodeAndStatus(command.classroomId(), "SAVINGS", "ACTIVE")) {
            throw operation(HttpStatus.CONFLICT, "MODULE_NOT_ACTIVE", "The savings module is not active.");
        }
        // Lock the logical product so two tabs cannot open the same savings product concurrently.
        FinancialProduct product = productRepository.findForUpdateByClassroomIdAndId(command.classroomId(), command.productId())
            .filter(FinancialProduct::isPublished)
            .orElseThrow(() -> operation(HttpStatus.CONFLICT, "PRODUCT_NOT_ACTIVE", "The savings product is not published."));
        FinancialProductVersion version = versionRepository
            .findTopByClassroomIdAndFinancialProductIdAndStatusOrderByVersionDesc(command.classroomId(), product.getId(), "PUBLISHED")
            .filter(candidate -> candidate.getProductType() == FinancialProductType.SAVINGS)
            .orElseThrow(() -> operation(HttpStatus.CONFLICT, "PRODUCT_NOT_ACTIVE", "The product has no published savings terms."));
        if (version.getVersion() != command.expectedProductVersion()) {
            throw operation(HttpStatus.CONFLICT, "PRODUCT_VERSION_CONFLICT", "The savings product terms have changed.");
        }
        if (accountRepository.existsForLogicalProduct(
            command.classroomId(),
            student.getId(),
            product.getId()
        )) {
            throw operation(
                HttpStatus.CONFLICT,
                "SAVINGS_PRODUCT_ALREADY_SUBSCRIBED",
                "A student can subscribe to each savings product only once."
            );
        }
        Integer count = version.getInstallmentCount();
        Long configuredAmount = version.getInstallmentAmount();
        if (count == null || configuredAmount == null || configuredAmount != command.installmentAmount() || !version.accepts(command.installmentAmount())) {
            throw operation(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SCHEDULE", "The installment amount does not match the savings product.");
        }
        Instant now = clock.instant();
        long total = Math.multiplyExact(command.installmentAmount(), count.longValue());
        long interest = interest(total, version.getInterestRateBps());
        Instant maturesAt = now.plus(Duration.ofDays(version.getTermUnits()));
        UUID groupId = uuidGenerator.generate();
        SavingsAccount account = SavingsAccount.open(uuidGenerator.generate(), command.classroomId(), student.getId(), version.getId(),
            command.installmentAmount(), count, interest, now, maturesAt, groupId);
        List<SavingsInstallment> installments = schedule(account, command.classroomId(), command.installmentAmount(), count, now, maturesAt, groupId);
        PolicySnapshot snapshot = snapshot(command, version, count, interest, maturesAt, now);
        LedgerAccount wallet = wallet(command.classroomId(), student.getId(), now);
        LedgerAccount liability = liability(command.classroomId(), now);
        LedgerDomainMutation openAccount = new LedgerDomainMutation() {
            @Override public void validate() { if (!version.accepts(command.installmentAmount())) throw operation(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SCHEDULE", "The savings product changed."); }
            @Override public void apply() { accountRepository.save(account); installmentRepository.saveAll(installments); }
        };
        transactionService.execute(groupId, LedgerTransactionCommand.original(command.classroomId(), "SAVINGS_OPEN", LedgerActorType.STUDENT,
            student.getId(), snapshot.getId(), ROUTE_KEY, command.requestId().toString(), requestHash, command.correlationId().toString(),
            "Open savings account and pay first installment", List.of(new LedgerTransactionPosting(wallet.getId(), -command.installmentAmount()),
                new LedgerTransactionPosting(liability.getId(), command.installmentAmount())), now), List.of(openAccount));
        accountRepository.flush();
        eventPublisher.publish(command.classroomId(), "STUDENT_PRIVATE", student.getId(), "savings.account.opened", "SAVINGS_ACCOUNT", account.getId(),
            account.getStateVersion(), Map.of(
                "account_id", account.getId().toString(),
                "product_id", product.getId().toString(),
                "product_name", product.getName(),
                "installment_amount", command.installmentAmount(),
                "status", account.getStatus().name()
            ), false);
        return new FinancialResourceResult(account.getId(), account.getStatus().name(), account.getStateVersion());
    }

    private List<SavingsInstallment> schedule(SavingsAccount account, UUID classroomId, long amount, int count, Instant now, Instant maturesAt, UUID groupId) {
        List<SavingsInstallment> result = new ArrayList<>();
        result.add(SavingsInstallment.paid(uuidGenerator.generate(), classroomId, account.getId(), amount, now, groupId));
        long spacingSeconds = Math.max(86_400L, Duration.between(now, maturesAt).getSeconds() / count);
        for (int number = 2; number <= count; number += 1) {
            Instant dueAt = now.plusSeconds(spacingSeconds * (number - 1L));
            result.add(SavingsInstallment.scheduled(uuidGenerator.generate(), classroomId, account.getId(), number, amount,
                dueAt, dueAt.plus(Duration.ofDays(1)), now));
        }
        return result;
    }

    private FinancialResourceResult replay(UUID classroomId, UUID studentId, UUID requestId, String requestHash) {
        IdempotencyRecord record = idempotencyRepository.findForReplay(classroomId, studentId, ROUTE_KEY, requestId.toString()).orElse(null);
        if (record == null) return null;
        if (!record.hasRequestHash(requestHash)) throw new IdempotencyKeyReusedException();
        SavingsAccount account = accountRepository.findByClassroomIdAndOpeningTransactionGroupId(classroomId, record.getTransactionGroupId())
            .orElseThrow(() -> operation(HttpStatus.CONFLICT, "IDEMPOTENCY_RESULT_MISSING", "The previous savings result could not be found."));
        return new FinancialResourceResult(account.getId(), account.getStatus().name(), account.getStateVersion());
    }

    private PolicySnapshot snapshot(SavingsSubscriptionCommand command, FinancialProductVersion version, int count, long interest, Instant maturesAt, Instant now) {
        String snapshotHash = hash(command.classroomId() + "|" + version.getId() + "|" + command.installmentAmount() + "|" + count + "|" + maturesAt);
        return snapshotRepository.findByClassroomIdAndSnapshotHash(command.classroomId(), snapshotHash).orElseGet(() -> snapshotRepository.save(PolicySnapshot.capture(
            uuidGenerator.generate(), command.classroomId(), snapshotHash, "1", Map.of("financial_product_version_id", version.getId(),
                "product_version", version.getVersion(), "installment_amount", command.installmentAmount(), "installment_count", count,
                "interest_rate_bps", version.getInterestRateBps(), "expected_interest_amount", interest, "matures_at", maturesAt.toString()), "HALF_UP", now)));
    }

    private LedgerAccount wallet(UUID classroomId, UUID studentId, Instant now) { return ledgerAccountRepository
        .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(classroomId, studentId, LedgerAccountType.STUDENT_WALLET, LedgerAccountStatus.ACTIVE)
        .orElseGet(() -> ledgerAccountRepository.save(LedgerAccount.studentWallet(uuidGenerator.generate(), classroomId, studentId, now))); }
    private LedgerAccount liability(UUID classroomId, Instant now) { return ledgerAccountRepository
        .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(classroomId, LIABILITY_KEY, LedgerAccountType.SAVINGS_LIABILITY, LedgerAccountStatus.ACTIVE)
        .orElseGet(() -> ledgerAccountRepository.save(LedgerAccount.systemAccount(uuidGenerator.generate(), classroomId, LIABILITY_KEY, LedgerAccountType.SAVINGS_LIABILITY, now))); }
    private Classroom requireActiveClassroom(UUID classroomId) { Classroom classroom = classroomRepository.findById(classroomId)
        .orElseThrow(() -> operation(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "The classroom could not be found."));
        if (classroom.getStatus() != ClassroomStatus.ACTIVE) throw operation(HttpStatus.CONFLICT, "CLASS_NOT_ACTIVE", "Savings are available only in an active classroom."); return classroom; }
    private static long interest(long amount, int rateBps) { try { return BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(rateBps)).divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP).longValueExact(); }
        catch (ArithmeticException exception) { throw operation(HttpStatus.UNPROCESSABLE_ENTITY, "AMOUNT_OVERFLOW", "The expected savings interest exceeds the supported range."); } }
    private static UUID studentId(String subject) { try { return UUID.fromString(subject); }
        catch (RuntimeException exception) { throw operation(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "A valid student session is required."); } }
    private static void validate(SavingsSubscriptionCommand command) { if (command == null || command.classroomId() == null || command.requestId() == null || command.correlationId() == null || command.productId() == null || command.installmentAmount() <= 0 || command.expectedProductVersion() < 1) throw operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Check the savings subscription values."); }
    private static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("Could not hash the savings request.", exception); } }
    private static ClassroomOperationException operation(HttpStatus status, String code, String message) { return new ClassroomOperationException(status, code, message); }
}
