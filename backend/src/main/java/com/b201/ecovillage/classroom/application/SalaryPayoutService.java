package com.b201.ecovillage.classroom.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.domain.Payslip;
import com.b201.ecovillage.classroom.domain.SalaryRole;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.classroom.repository.PayslipRepository;
import com.b201.ecovillage.classroom.repository.SalaryRoleRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStatus;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.application.AtomicLedgerTransactionService;
import com.b201.ecovillage.ledger.application.LedgerDomainMutation;
import com.b201.ecovillage.ledger.application.LedgerTransactionCommand;
import com.b201.ecovillage.ledger.application.LedgerTransactionPosting;
import com.b201.ecovillage.ledger.application.LedgerTransactionResult;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.LedgerActorType;
import com.b201.ecovillage.ledger.domain.PolicySnapshot;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ledger.repository.PolicySnapshotRepository;

import tools.jackson.databind.ObjectMapper;

@Service
public class SalaryPayoutService {

    private static final String SALARY_POLICY_AREA = "SALARY";
    private static final String MONEY_ISSUANCE_KEY = "MONEY_ISSUANCE";
    private static final String CLASSROOM_FUND_KEY = "CLASSROOM_FUND";

    private final ClassroomAuthorizationService authorizationService;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRepository studentRepository;
    private final SalaryRoleRepository salaryRoleRepository;
    private final ClassroomPolicyVersionRepository policyVersionRepository;
    private final PayslipRepository payslipRepository;
    private final UserAccountRepository userAccountRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final PolicySnapshotRepository policySnapshotRepository;
    private final AtomicLedgerTransactionService transactionService;
    private final UuidV7Generator uuidGenerator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SalaryPayoutService(
        ClassroomAuthorizationService authorizationService,
        ClassroomRepository classroomRepository,
        ClassroomStudentRepository studentRepository,
        SalaryRoleRepository salaryRoleRepository,
        ClassroomPolicyVersionRepository policyVersionRepository,
        PayslipRepository payslipRepository,
        UserAccountRepository userAccountRepository,
        LedgerAccountRepository ledgerAccountRepository,
        PolicySnapshotRepository policySnapshotRepository,
        AtomicLedgerTransactionService transactionService,
        UuidV7Generator uuidGenerator,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.classroomRepository = classroomRepository;
        this.studentRepository = studentRepository;
        this.salaryRoleRepository = salaryRoleRepository;
        this.policyVersionRepository = policyVersionRepository;
        this.payslipRepository = payslipRepository;
        this.userAccountRepository = userAccountRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.policySnapshotRepository = policySnapshotRepository;
        this.transactionService = transactionService;
        this.uuidGenerator = uuidGenerator;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public SalaryPayoutResult pay(SalaryPayoutCommand command) {
        validateCommand(command);
        authorizationService.authorize(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.MANAGE_PAYOUTS,
            null
        );
        UserAccount teacher = userAccountRepository
            .findByExternalSubject(command.teacherSubject())
            .orElseThrow(() -> operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "교사 인증 정보를 확인할 수 없습니다."
            ));
        Classroom classroom = classroomRepository
            .findById(command.classroomId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "경제교실을 찾을 수 없습니다."
            ));
        if (classroom.getStatus() != ClassroomStatus.ACTIVE) {
            throw operation(
                HttpStatus.CONFLICT,
                "CLASS_NOT_ACTIVE",
                "활성 경제교실에서만 월급을 지급할 수 있습니다."
            );
        }
        if (
            command.expectedStateVersion() != null
                && command.expectedStateVersion() != classroom.getStateVersion()
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "경제교실 상태가 변경되었습니다."
            );
        }

        ClassroomPolicyVersion policy = policyVersionRepository
            .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
                command.classroomId(),
                SALARY_POLICY_AREA
            )
            .orElseThrow(() -> operation(
                HttpStatus.CONFLICT,
                "POLICY_VERSION_CONFLICT",
                "현재 급여 정책을 확인할 수 없습니다."
            ));
        if (policy.getVersion() != command.policyVersion()) {
            throw operation(
                HttpStatus.CONFLICT,
                "POLICY_VERSION_CONFLICT",
                "요청한 급여 정책 버전이 현재 버전과 다릅니다."
            );
        }

        List<UUID> studentIds = command.studentIds().stream().distinct().sorted().toList();
        if (studentIds.size() != command.studentIds().size()) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "student_ids에는 중복된 학생을 넣을 수 없습니다."
            );
        }
        List<ClassroomStudent> students = studentRepository.findAllForUpdate(
            command.classroomId(),
            studentIds,
            ClassroomStudentStatus.ACTIVE
        );
        if (students.size() != studentIds.size()) {
            throw operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "지급 대상 학생 중 현재 학급의 활성 학생이 아닌 대상이 있습니다."
            );
        }

        Map<UUID, SalaryRole> roles = salaryRoleRepository
            .findAllById(
                students.stream().map(ClassroomStudent::getSalaryRoleId).distinct().toList()
            )
            .stream()
            .collect(Collectors.toMap(SalaryRole::getId, Function.identity()));
        List<CalculatedPayslip> calculated = students.stream()
            .map(student -> calculate(student, roles.get(student.getSalaryRoleId())))
            .toList();
        long totalGross = sum(calculated, CalculatedPayslip::grossAmount);
        long totalDeduction = sum(calculated, CalculatedPayslip::deductionAmount);
        long totalTax = sum(calculated, CalculatedPayslip::taxAmount);
        long totalNet = sum(calculated, CalculatedPayslip::netAmount);
        if (totalGross <= 0) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "POLICY_VIOLATION",
                "지급할 세전 급여 합계는 0보다 커야 합니다."
            );
        }
        if (command.amount() != 0 && command.amount() != totalGross) {
            throw operation(
                HttpStatus.CONFLICT,
                "POLICY_VIOLATION",
                "요청 금액이 현재 역할별 세전 급여 합계와 다릅니다."
            );
        }

        Instant occurredAt = command.occurredAt() == null
            ? clock.instant()
            : command.occurredAt();
        Map<String, Object> snapshotValues = snapshotValues(
            policy,
            command.periodKey(),
            calculated,
            totalGross,
            totalDeduction,
            totalTax,
            totalNet
        );
        String snapshotHash = sha256(snapshotValues);
        PolicySnapshot snapshot = policySnapshotRepository
            .findByClassroomIdAndSnapshotHash(command.classroomId(), snapshotHash)
            .orElseGet(() -> policySnapshotRepository.save(
                PolicySnapshot.capture(
                    uuidGenerator.generate(),
                    command.classroomId(),
                    snapshotHash,
                    "1",
                    snapshotValues,
                    "HALF_UP",
                    occurredAt
                )
            ));

        LedgerAccount issuance = systemAccount(
            command.classroomId(),
            MONEY_ISSUANCE_KEY,
            LedgerAccountType.MONEY_ISSUANCE,
            occurredAt
        );
        LedgerAccount fund = totalDeduction + totalTax == 0
            ? null
            : systemAccount(
                command.classroomId(),
                CLASSROOM_FUND_KEY,
                LedgerAccountType.CLASSROOM_FUND,
                occurredAt
            );
        Map<UUID, LedgerAccount> wallets = new LinkedHashMap<>();
        for (CalculatedPayslip item : calculated) {
            if (item.netAmount() > 0) {
                wallets.put(
                    item.studentId(),
                    studentWallet(command.classroomId(), item.studentId(), occurredAt)
                );
            }
        }

        List<LedgerTransactionPosting> postings = new ArrayList<>();
        postings.add(new LedgerTransactionPosting(issuance.getId(), -totalGross));
        calculated.stream()
            .filter(item -> item.netAmount() > 0)
            .forEach(item -> postings.add(
                new LedgerTransactionPosting(
                    wallets.get(item.studentId()).getId(),
                    item.netAmount()
                )
            ));
        if (fund != null) {
            postings.add(
                new LedgerTransactionPosting(fund.getId(), totalDeduction + totalTax)
            );
        }

        UUID transactionGroupId = uuidGenerator.generate();
        List<Payslip> payslips = calculated.stream()
            .map(item -> Payslip.issue(
                uuidGenerator.generate(),
                command.classroomId(),
                item.studentId(),
                item.salaryRoleId(),
                command.periodKey(),
                item.grossAmount(),
                item.deductionAmount(),
                item.taxAmount(),
                snapshot.getId(),
                transactionGroupId,
                occurredAt
            ))
            .toList();
        LedgerDomainMutation payslipMutation = new LedgerDomainMutation() {
            @Override
            public void validate() {
                if (
                    payslipRepository.existsByClassroomStudentIdInAndPeriodKey(
                        studentIds,
                        command.periodKey()
                    )
                ) {
                    throw operation(
                        HttpStatus.CONFLICT,
                        "DUPLICATE_PAYOUT",
                        "같은 급여 회차에 이미 지급된 학생이 있습니다."
                    );
                }
            }

            @Override
            public void apply() {
                payslipRepository.saveAll(payslips);
            }
        };

        LedgerTransactionResult result = transactionService.execute(
            transactionGroupId,
            LedgerTransactionCommand.original(
                command.classroomId(),
                "SALARY_PAYOUT",
                LedgerActorType.TEACHER,
                teacher.getId(),
                snapshot.getId(),
                "/classrooms/{classroom_id}/payouts",
                command.requestId().toString(),
                sha256(Map.of(
                    "period_key", command.periodKey(),
                    "policy_version", command.policyVersion(),
                    "student_ids", studentIds,
                    "amount", command.amount(),
                    "reason", command.reason()
                )),
                command.correlationId().toString(),
                command.reason(),
                postings,
                occurredAt
            ),
            List.of(payslipMutation)
        );
        return new SalaryPayoutResult(
            result.transactionGroupId(),
            result.status().name(),
            result.stateVersion()
        );
    }

    private LedgerAccount studentWallet(UUID classroomId, UUID studentId, Instant now) {
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
            .orElseGet(() -> ledgerAccountRepository.save(
                LedgerAccount.systemAccount(
                    uuidGenerator.generate(),
                    classroomId,
                    ownerKey,
                    accountType,
                    now
                )
            ));
    }

    private CalculatedPayslip calculate(ClassroomStudent student, SalaryRole role) {
        if (
            role == null
                || !role.getClassroomId().equals(student.getClassroomId())
                || !"ACTIVE".equals(role.getStatus())
        ) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "SALARY_ROLE_NOT_FOUND",
                "학생에게 유효한 급여 역할이 지정되지 않았습니다."
            );
        }
        Map<String, Object> deductions = role.getDeductions();
        long fixed = numericLong(deductions.get("fixed_amount"), "fixed_amount", 0, role.getGrossAmount());
        long taxRateBps = numericLong(deductions.get("tax_rate_bps"), "tax_rate_bps", 0, 10_000);
        long taxableAmount = role.getGrossAmount() - fixed;
        long tax = BigDecimal.valueOf(taxableAmount)
            .multiply(BigDecimal.valueOf(taxRateBps))
            .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
            .longValueExact();
        return new CalculatedPayslip(
            student.getId(),
            role.getId(),
            role.getRoleCode(),
            role.getGrossAmount(),
            fixed,
            tax,
            role.getGrossAmount() - fixed - tax
        );
    }

    private long numericLong(Object value, String field, long minimum, long maximum) {
        if (value == null) {
            return 0;
        }
        long parsed;
        try {
            parsed = value instanceof Number number
                ? new BigDecimal(number.toString()).longValueExact()
                : Long.parseLong(value.toString());
        } catch (ArithmeticException | NumberFormatException exception) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "POLICY_VIOLATION",
                field + " 값은 정수여야 합니다."
            );
        }
        if (parsed < minimum || parsed > maximum) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "POLICY_VIOLATION",
                field + " 값이 허용 범위를 벗어났습니다."
            );
        }
        return parsed;
    }

    private Map<String, Object> snapshotValues(
        ClassroomPolicyVersion policy,
        String periodKey,
        List<CalculatedPayslip> calculated,
        long totalGross,
        long totalDeduction,
        long totalTax,
        long totalNet
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("policy_area", policy.getPolicyArea());
        values.put("policy_version", policy.getVersion());
        values.put("policy_values", policy.getPolicyValues());
        values.put("period_key", periodKey);
        values.put(
            "payslips",
            calculated.stream().map(item -> Map.of(
                "student_id", item.studentId(),
                "salary_role_id", item.salaryRoleId(),
                "role_code", item.roleCode(),
                "gross_amount", item.grossAmount(),
                "deduction_amount", item.deductionAmount(),
                "tax_amount", item.taxAmount(),
                "net_amount", item.netAmount()
            )).toList()
        );
        values.put("total_gross_amount", totalGross);
        values.put("total_deduction_amount", totalDeduction);
        values.put("total_tax_amount", totalTax);
        values.put("total_net_amount", totalNet);
        return values;
    }

    private String sha256(Object value) {
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(value);
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(serialized)
            );
        } catch (Exception exception) {
            throw new IllegalStateException("지급 요청 해시를 생성할 수 없습니다.", exception);
        }
    }

    private static long sum(
        List<CalculatedPayslip> values,
        java.util.function.ToLongFunction<CalculatedPayslip> mapper
    ) {
        long result = 0;
        for (CalculatedPayslip value : values) {
            result = Math.addExact(result, mapper.applyAsLong(value));
        }
        return result;
    }

    private static void validateCommand(SalaryPayoutCommand command) {
        if (
            command == null
                || command.classroomId() == null
                || command.requestId() == null
                || command.correlationId() == null
                || command.payoutType() != PayoutType.SALARY
                || command.studentIds() == null
                || command.studentIds().isEmpty()
                || command.studentIds().stream().anyMatch(java.util.Objects::isNull)
                || command.periodKey() == null
                || command.periodKey().isBlank()
                || command.periodKey().trim().length() > 40
                || command.amount() < 0
                || command.policyVersion() < 1
                || command.reason() == null
                || command.reason().isBlank()
        ) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "월급 지급 요청 값을 확인해 주세요."
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

    private record CalculatedPayslip(
        UUID studentId,
        UUID salaryRoleId,
        String roleCode,
        long grossAmount,
        long deductionAmount,
        long taxAmount,
        long netAmount
    ) {
    }
}
