package com.b201.ecovillage.trade.application;

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

import com.b201.ecovillage.audit.application.AuditTrailService;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
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
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.PolicySnapshot;
import com.b201.ecovillage.ledger.domain.TransactionGroupStatus;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ledger.repository.PolicySnapshotRepository;
import com.b201.ecovillage.trade.domain.InventoryItem;
import com.b201.ecovillage.trade.domain.InventoryItemStatus;
import com.b201.ecovillage.trade.domain.Trade;
import com.b201.ecovillage.trade.domain.TradeItemLeg;
import com.b201.ecovillage.trade.domain.TradeMoneyLeg;
import com.b201.ecovillage.trade.domain.TradeStatus;
import com.b201.ecovillage.trade.repository.InventoryItemRepository;
import com.b201.ecovillage.trade.repository.TradeDecisionRepository;
import com.b201.ecovillage.trade.repository.TradeItemLegRepository;
import com.b201.ecovillage.trade.repository.TradeMoneyLegRepository;
import com.b201.ecovillage.trade.repository.TradeRepository;

class TradeTeacherDecisionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    private TradeRepository tradeRepository;
    private TradeDecisionRepository decisionRepository;
    private AtomicLedgerTransactionService transactionService;
    private TradeTeacherDecisionService service;
    private TeacherTradeDecisionRequest command;
    private Trade trade;
    private InventoryItem item;
    private LedgerAccount proposerWallet;

    @BeforeEach
    void setUp() {
        ClassroomAuthorizationService authorizationService =
            mock(ClassroomAuthorizationService.class);
        UserAccountRepository userRepository = mock(UserAccountRepository.class);
        ClassroomRepository classroomRepository = mock(ClassroomRepository.class);
        ClassroomStudentRepository studentRepository =
            mock(ClassroomStudentRepository.class);
        ClassroomPolicyVersionRepository policyRepository =
            mock(ClassroomPolicyVersionRepository.class);
        tradeRepository = mock(TradeRepository.class);
        TradeMoneyLegRepository moneyRepository = mock(TradeMoneyLegRepository.class);
        TradeItemLegRepository itemLegRepository = mock(TradeItemLegRepository.class);
        decisionRepository = mock(TradeDecisionRepository.class);
        InventoryItemRepository inventoryRepository = mock(InventoryItemRepository.class);
        LedgerAccountRepository accountRepository = mock(LedgerAccountRepository.class);
        PolicySnapshotRepository snapshotRepository = mock(PolicySnapshotRepository.class);
        IdempotencyRecordRepository idempotencyRepository =
            mock(IdempotencyRecordRepository.class);
        transactionService = mock(AtomicLedgerTransactionService.class);
        AuditTrailService auditTrailService = mock(AuditTrailService.class);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        UuidV7Generator uuidGenerator = new UuidV7Generator(clock, new SecureRandom());
        service = new TradeTeacherDecisionService(
            authorizationService,
            userRepository,
            classroomRepository,
            studentRepository,
            policyRepository,
            tradeRepository,
            moneyRepository,
            itemLegRepository,
            decisionRepository,
            inventoryRepository,
            accountRepository,
            snapshotRepository,
            idempotencyRepository,
            transactionService,
            auditTrailService,
            uuidGenerator,
            clock
        );

        UUID classroomId = UUID.randomUUID();
        UUID proposerId = UUID.randomUUID();
        UUID counterpartId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID tradeId = UUID.randomUUID();
        String subject = "teacher-subject";
        trade = Trade.propose(
            tradeId,
            classroomId,
            proposerId,
            counterpartId,
            "item and money",
            NOW.plusSeconds(3600),
            NOW.minusSeconds(60)
        );
        trade.acceptByCounterpart(counterpartId, NOW.minusSeconds(30));
        command = new TeacherTradeDecisionRequest(
            subject,
            classroomId,
            tradeId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            0L,
            TeacherTradeDecision.APPROVE,
            "approved in class"
        );
        ClassroomStudent proposer = ClassroomStudent.enroll(
            proposerId,
            classroomId,
            1,
            "proposer",
            NOW.minusSeconds(100)
        );
        ClassroomStudent counterpart = ClassroomStudent.enroll(
            counterpartId,
            classroomId,
            2,
            "counterpart",
            NOW.minusSeconds(100)
        );
        UserAccount teacher = UserAccount.createTeacher(
            teacherId,
            subject,
            "teacher",
            NOW.minusSeconds(100)
        );
        TradeMoneyLeg moneyLeg = TradeMoneyLeg.create(
            UUID.randomUUID(),
            classroomId,
            tradeId,
            proposerId,
            counterpartId,
            1_000,
            NOW.minusSeconds(60)
        );
        item = InventoryItem.acquire(
            UUID.randomUUID(),
            classroomId,
            proposerId,
            UUID.randomUUID(),
            null,
            NOW.minusSeconds(100)
        );
        item.lockForTrade(tradeId, proposerId, NOW.minusSeconds(60));
        TradeItemLeg itemLeg = TradeItemLeg.create(
            UUID.randomUUID(),
            classroomId,
            tradeId,
            item.getId(),
            proposerId,
            counterpartId,
            NOW.minusSeconds(60)
        );
        proposerWallet = LedgerAccount.studentWallet(
            UUID.randomUUID(),
            classroomId,
            proposerId,
            NOW.minusSeconds(100)
        );
        proposerWallet.post(2_000, NOW.minusSeconds(90));
        LedgerAccount counterpartWallet = LedgerAccount.studentWallet(
            UUID.randomUUID(),
            classroomId,
            counterpartId,
            NOW.minusSeconds(100)
        );
        LedgerAccount fund = LedgerAccount.systemAccount(
            UUID.randomUUID(),
            classroomId,
            "CLASSROOM_FUND",
            LedgerAccountType.CLASSROOM_FUND,
            NOW.minusSeconds(100)
        );
        ClassroomPolicyVersion policy = ClassroomPolicyVersion.salary(
            UUID.randomUUID(),
            classroomId,
            1,
            Map.of("trade_tax_rate_bps", 1_000),
            "TEACHER",
            NOW.minusSeconds(100)
        );
        PolicySnapshot snapshot = PolicySnapshot.capture(
            UUID.randomUUID(),
            classroomId,
            "snapshot-hash",
            "1",
            Map.of("trade_tax_rate_bps", 1_000),
            "HALF_UP",
            NOW
        );

        when(userRepository.findByExternalSubject(subject)).thenReturn(Optional.of(teacher));
        when(classroomRepository.findById(classroomId))
            .thenReturn(Optional.of(Classroom.active(classroomId, "ABCD1234", NOW)));
        when(
            idempotencyRepository.findForReplay(
                eq(classroomId),
                eq(teacherId),
                any(),
                eq(command.requestId().toString())
            )
        ).thenReturn(Optional.empty());
        when(tradeRepository.findForUpdate(classroomId, tradeId))
            .thenReturn(Optional.of(trade));
        when(
            studentRepository.findAllForUpdate(
                eq(classroomId),
                any(),
                eq(ClassroomStudentStatus.ACTIVE)
            )
        ).thenReturn(List.of(proposer, counterpart));
        when(moneyRepository.findAllByTradeIdOrderByFromStudentId(tradeId))
            .thenReturn(List.of(moneyLeg));
        when(itemLegRepository.findAllByTradeIdOrderByInventoryItemId(tradeId))
            .thenReturn(List.of(itemLeg));
        when(inventoryRepository.findAllForUpdate(classroomId, List.of(item.getId())))
            .thenReturn(List.of(item));
        when(
            policyRepository.findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
                classroomId,
                "ECONOMIC"
            )
        ).thenReturn(Optional.empty());
        when(
            policyRepository.findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
                classroomId,
                "SALARY"
            )
        ).thenReturn(Optional.of(policy));
        when(snapshotRepository.findByClassroomIdAndSnapshotHash(eq(classroomId), any()))
            .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenReturn(snapshot);
        when(
            accountRepository
                .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                    classroomId,
                    proposerId,
                    LedgerAccountType.STUDENT_WALLET,
                    LedgerAccountStatus.ACTIVE
                )
        ).thenReturn(Optional.of(proposerWallet));
        when(
            accountRepository
                .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                    classroomId,
                    counterpartId,
                    LedgerAccountType.STUDENT_WALLET,
                    LedgerAccountStatus.ACTIVE
                )
        ).thenReturn(Optional.of(counterpartWallet));
        when(
            accountRepository
                .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                    classroomId,
                    "CLASSROOM_FUND",
                    LedgerAccountType.CLASSROOM_FUND,
                    LedgerAccountStatus.ACTIVE
                )
        ).thenReturn(Optional.of(fund));
        doAnswer(invocation -> {
            UUID groupId = invocation.getArgument(0);
            List<?> mutations = invocation.getArgument(2);
            mutations.forEach(value -> ((LedgerDomainMutation) value).validate());
            mutations.forEach(value -> ((LedgerDomainMutation) value).apply());
            return new LedgerTransactionResult(
                groupId,
                TransactionGroupStatus.COMMITTED,
                1,
                false
            );
        }).when(transactionService).execute(any(UUID.class), any(), anyList());
        when(tradeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(decisionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotencyRepository.save(any(IdempotencyRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void approvesMoneyTaxAndItemAsOneSettlement() {
        TradeCommandResult result = service.decide(command);

        assertEquals("COMPLETED", result.status());
        assertEquals(TradeStatus.COMPLETED, trade.getStatus());
        assertEquals(InventoryItemStatus.AVAILABLE, item.getStatus());
        assertEquals(trade.getCounterpartStudentId(), item.getClassroomStudentId());
        ArgumentCaptor<LedgerTransactionCommand> ledger =
            ArgumentCaptor.forClass(LedgerTransactionCommand.class);
        verify(transactionService).execute(any(UUID.class), ledger.capture(), anyList());
        assertEquals(
            List.of(-1_100L, 100L, 1_000L),
            ledger.getValue().postings().stream()
                .map(posting -> posting.amount())
                .sorted()
                .toList()
        );
        verify(decisionRepository).save(any());
    }

    @Test
    void insufficientGrossBalanceLeavesTradeAndItemUnchanged() {
        proposerWallet.post(-1_600, NOW.minusSeconds(1));

        ClassroomOperationException exception = assertThrows(
            ClassroomOperationException.class,
            () -> service.decide(command)
        );

        assertEquals("INSUFFICIENT_BALANCE", exception.getCode());
        assertEquals(TradeStatus.AWAITING_TEACHER, trade.getStatus());
        assertEquals(InventoryItemStatus.LOCKED_TRADE, item.getStatus());
        verify(decisionRepository, never()).save(any());
    }
}
