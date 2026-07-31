package com.b201.ecovillage.classroom.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.domain.SalaryRole;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.classroom.repository.PayslipRepository;
import com.b201.ecovillage.classroom.repository.SalaryRoleRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.application.AtomicLedgerTransactionService;
import com.b201.ecovillage.ledger.application.LedgerDomainMutation;
import com.b201.ecovillage.ledger.application.LedgerTransactionCommand;
import com.b201.ecovillage.ledger.application.LedgerTransactionResult;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.PolicySnapshot;
import com.b201.ecovillage.ledger.domain.TransactionGroupStatus;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ledger.repository.PolicySnapshotRepository;

import tools.jackson.databind.ObjectMapper;

class SalaryPayoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");
    private static final String SUBJECT = "teacher-subject";

    private ClassroomAuthorizationService authorizationService;
    private ClassroomRepository classroomRepository;
    private ClassroomStudentRepository studentRepository;
    private SalaryRoleRepository salaryRoleRepository;
    private ClassroomPolicyVersionRepository policyVersionRepository;
    private ClassroomModuleRepository moduleRepository;
    private PayslipRepository payslipRepository;
    private UserAccountRepository userAccountRepository;
    private LedgerAccountRepository ledgerAccountRepository;
    private PolicySnapshotRepository policySnapshotRepository;
    private AtomicLedgerTransactionService transactionService;
    private SalaryPayoutService service;
    private UUID classroomId;
    private UUID studentId;

    @BeforeEach
    void setUp() {
        authorizationService = mock(ClassroomAuthorizationService.class);
        classroomRepository = mock(ClassroomRepository.class);
        studentRepository = mock(ClassroomStudentRepository.class);
        salaryRoleRepository = mock(SalaryRoleRepository.class);
        policyVersionRepository = mock(ClassroomPolicyVersionRepository.class);
        moduleRepository = mock(ClassroomModuleRepository.class);
        payslipRepository = mock(PayslipRepository.class);
        userAccountRepository = mock(UserAccountRepository.class);
        ledgerAccountRepository = mock(LedgerAccountRepository.class);
        policySnapshotRepository = mock(PolicySnapshotRepository.class);
        transactionService = mock(AtomicLedgerTransactionService.class);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        UuidV7Generator uuidGenerator = new UuidV7Generator(clock, new SecureRandom());
        service = new SalaryPayoutService(
            authorizationService,
            classroomRepository,
            studentRepository,
            salaryRoleRepository,
            policyVersionRepository,
            moduleRepository,
            payslipRepository,
            userAccountRepository,
            ledgerAccountRepository,
            policySnapshotRepository,
            transactionService,
            uuidGenerator,
            new ObjectMapper(),
            clock
        );

        classroomId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        ClassroomStudent student = ClassroomStudent.enroll(
            studentId,
            classroomId,
            1,
            "eco",
            NOW
        );
        student.assignSalaryRole(roleId, NOW);
        SalaryRole role = SalaryRole.create(
            roleId,
            classroomId,
            "LEADER",
            "반장",
            1_000,
            Map.of("fixed_amount", 100, "tax_rate_bps", 500),
            1,
            NOW
        );
        ClassroomPolicyVersion policy = ClassroomPolicyVersion.salary(
            UUID.randomUUID(),
            classroomId,
            2,
            Map.of("roles", List.of()),
            "TEACHER",
            NOW
        );
        UserAccount teacher = UserAccount.createTeacher(
            UUID.randomUUID(),
            SUBJECT,
            "teacher",
            NOW
        );

        when(classroomRepository.findById(classroomId))
            .thenReturn(Optional.of(Classroom.active(classroomId, "ABCD1234", NOW)));
        when(userAccountRepository.findByExternalSubject(SUBJECT))
            .thenReturn(Optional.of(teacher));
        when(
            policyVersionRepository
                .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(classroomId, "SALARY")
        ).thenReturn(Optional.of(policy));
        when(moduleRepository.existsByClassroomIdAndModuleCodeAndStatus(
            classroomId,
            "ROLE_SALARY",
            "ACTIVE"
        )).thenReturn(true);
        when(
            studentRepository.findAllForUpdate(
                eq(classroomId),
                anyList(),
                eq(ClassroomStudentStatus.ACTIVE)
            )
        ).thenReturn(List.of(student));
        when(salaryRoleRepository.findAllById(any())).thenReturn(List.of(role));
        when(policySnapshotRepository.findByClassroomIdAndSnapshotHash(eq(classroomId), any()))
            .thenReturn(Optional.empty());
        when(policySnapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(
            ledgerAccountRepository
                .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                    eq(classroomId),
                    eq(studentId),
                    eq(LedgerAccountType.STUDENT_WALLET),
                    eq(LedgerAccountStatus.ACTIVE)
                )
        ).thenReturn(Optional.empty());
        when(
            ledgerAccountRepository
                .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                    eq(classroomId),
                    any(),
                    any(),
                    eq(LedgerAccountStatus.ACTIVE)
                )
        ).thenReturn(Optional.empty());
        when(ledgerAccountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(payslipRepository.existsByClassroomStudentIdInAndPeriodKey(any(), any()))
            .thenReturn(false);
        doAnswer(invocation -> {
            UUID groupId = invocation.getArgument(0);
            List<?> mutations = invocation.getArgument(2);
            for (Object mutation : mutations) {
                ((LedgerDomainMutation) mutation).validate();
                ((LedgerDomainMutation) mutation).apply();
            }
            return new LedgerTransactionResult(
                groupId,
                TransactionGroupStatus.COMMITTED,
                2,
                false
            );
        }).when(transactionService).execute(any(UUID.class), any(), anyList());
    }

    @Test
    void calculatesTaxAndCreatesBalancedLedgerCommand() {
        SalaryPayoutResult result = service.pay(command("SESSION-03"));

        assertEquals("COMMITTED", result.status());
        ArgumentCaptor<LedgerTransactionCommand> commandCaptor =
            ArgumentCaptor.forClass(LedgerTransactionCommand.class);
        verify(transactionService).execute(
            any(UUID.class),
            commandCaptor.capture(),
            anyList()
        );
        List<Long> amounts = commandCaptor.getValue().postings().stream()
            .map(posting -> posting.amount())
            .sorted()
            .toList();
        assertEquals(List.of(-1_000L, 145L, 855L), amounts);
        assertEquals(0, amounts.stream().mapToLong(Long::longValue).sum());
        verify(payslipRepository).saveAll(any());
    }

    @Test
    void rejectsDuplicatePeriodBeforeApplyingPayslipMutation() {
        when(
            payslipRepository.existsByClassroomStudentIdInAndPeriodKey(
                any(),
                eq("SESSION-03")
            )
        ).thenReturn(true);

        ClassroomOperationException exception = assertThrows(
            ClassroomOperationException.class,
            () -> service.pay(command("SESSION-03"))
        );

        assertEquals("DUPLICATE_PAYOUT", exception.getCode());
        verify(payslipRepository, never()).saveAll(any());
    }

    @Test
    void rejectsSalaryPayoutWhenRoleSalaryModuleIsInactive() {
        when(moduleRepository.existsByClassroomIdAndModuleCodeAndStatus(
            classroomId,
            "ROLE_SALARY",
            "ACTIVE"
        )).thenReturn(false);

        ClassroomOperationException exception = assertThrows(
            ClassroomOperationException.class,
            () -> service.pay(command("SESSION-03"))
        );

        assertEquals("MODULE_NOT_ACTIVE", exception.getCode());
        verify(transactionService, never()).execute(any(UUID.class), any(), anyList());
        verify(payslipRepository, never()).saveAll(any());
    }

    private SalaryPayoutCommand command(String periodKey) {
        return new SalaryPayoutCommand(
            SUBJECT,
            classroomId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            PayoutType.SALARY,
            List.of(studentId),
            periodKey,
            1_000,
            2,
            "3회차 역할 월급",
            0L,
            NOW
        );
    }
}
