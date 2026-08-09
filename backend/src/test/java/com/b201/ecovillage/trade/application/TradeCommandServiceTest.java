package com.b201.ecovillage.trade.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.character.repository.CharacterEquipmentRepository;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.trade.domain.Trade;
import com.b201.ecovillage.trade.domain.InventoryItem;
import com.b201.ecovillage.trade.domain.TradeMoneyLeg;
import com.b201.ecovillage.trade.domain.TradeStatus;
import com.b201.ecovillage.trade.repository.InventoryItemRepository;
import com.b201.ecovillage.trade.repository.TradeDecisionRepository;
import com.b201.ecovillage.trade.repository.TradeItemLegRepository;
import com.b201.ecovillage.trade.repository.TradeMoneyLegRepository;
import com.b201.ecovillage.trade.repository.TradeRepository;

class TradeCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    private TradeRepository tradeRepository;
    private TradeMoneyLegRepository moneyLegRepository;
    private ClassroomStudentRepository studentRepository;
    private IdempotencyRecordRepository idempotencyRepository;
    private InventoryItemRepository inventoryRepository;
    private CharacterEquipmentRepository equipmentRepository;
    private LedgerAccountRepository accountRepository;
    private TradeTaxPolicyResolver taxPolicyResolver;
    private LedgerAccount proposerWallet;
    private ClassroomStudent counterpartStudent;
    private TradeCommandService service;
    private CreateTradeCommand command;
    private AtomicReference<Trade> savedTrade;
    private AtomicReference<IdempotencyRecord> savedIdempotency;

    @BeforeEach
    void setUp() {
        ClassroomAuthorizationService authorizationService =
            mock(ClassroomAuthorizationService.class);
        ClassroomRepository classroomRepository = mock(ClassroomRepository.class);
        studentRepository = mock(ClassroomStudentRepository.class);
        tradeRepository = mock(TradeRepository.class);
        moneyLegRepository = mock(TradeMoneyLegRepository.class);
        TradeItemLegRepository itemLegRepository = mock(TradeItemLegRepository.class);
        TradeDecisionRepository decisionRepository = mock(TradeDecisionRepository.class);
        inventoryRepository = mock(InventoryItemRepository.class);
        equipmentRepository = mock(CharacterEquipmentRepository.class);
        idempotencyRepository = mock(IdempotencyRecordRepository.class);
        accountRepository = mock(LedgerAccountRepository.class);
        taxPolicyResolver = mock(TradeTaxPolicyResolver.class);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        UuidV7Generator uuidGenerator = new UuidV7Generator(clock, new SecureRandom());
        service = new TradeCommandService(
            authorizationService,
            classroomRepository,
            studentRepository,
            tradeRepository,
            moneyLegRepository,
            itemLegRepository,
            decisionRepository,
            inventoryRepository,
            equipmentRepository,
            idempotencyRepository,
            accountRepository,
            taxPolicyResolver,
            uuidGenerator,
            clock
        );

        UUID classroomId = UUID.randomUUID();
        UUID proposerId = UUID.randomUUID();
        UUID counterpartId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        command = new CreateTradeCommand(
            proposerId.toString(),
            classroomId,
            requestId,
            UUID.randomUUID(),
            0L,
            counterpartId,
            500,
            0,
            List.of(),
            List.of(),
            "money transfer"
        );
        ClassroomStudent proposer = ClassroomStudent.enroll(
            proposerId,
            classroomId,
            1,
            "proposer",
            NOW
        );
        counterpartStudent = ClassroomStudent.enroll(
            counterpartId,
            classroomId,
            2,
            "counterpart",
            NOW
        );
        proposerWallet = LedgerAccount.studentWallet(
            UUID.randomUUID(),
            classroomId,
            proposerId,
            NOW.minusSeconds(10)
        );
        proposerWallet.post(1_000, NOW.minusSeconds(5));

        when(classroomRepository.findById(classroomId))
            .thenReturn(Optional.of(Classroom.active(classroomId, "ABCD1234", NOW)));
        when(
            studentRepository.findAllForUpdate(
                eq(classroomId),
                any(),
                eq(ClassroomStudentStatus.ACTIVE)
            )
        ).thenReturn(List.of(proposer, counterpartStudent));
        when(
            idempotencyRepository.findForReplay(
                eq(classroomId),
                eq(proposerId),
                any(),
                eq(requestId.toString())
            )
        ).thenReturn(Optional.empty());
        when(taxPolicyResolver.resolve(classroomId)).thenReturn(new TradeTaxPolicy(
            500,
            null,
            0,
            "SYSTEM_DEFAULT"
        ));
        when(accountRepository.findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
            classroomId,
            proposerId,
            LedgerAccountType.STUDENT_WALLET,
            LedgerAccountStatus.ACTIVE
        )).thenReturn(Optional.of(proposerWallet));
        savedTrade = new AtomicReference<>();
        when(tradeRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Trade trade = invocation.getArgument(0);
            savedTrade.set(trade);
            return trade;
        });
        when(moneyLegRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        savedIdempotency = new AtomicReference<>();
        when(idempotencyRepository.save(any())).thenAnswer(invocation -> {
            IdempotencyRecord record = invocation.getArgument(0);
            savedIdempotency.set(record);
            return record;
        });
    }

    @Test
    void createsImmutableProposalAndReplaysSameIdempotencyKey() {
        TradeCommandResult first = service.create(command);
        assertEquals("AWAITING_COUNTERPART", first.status());
        verify(moneyLegRepository).save(any());

        when(
            idempotencyRepository.findForReplay(
                eq(command.classroomId()),
                eq(UUID.fromString(command.studentSubject())),
                any(),
                eq(command.requestId().toString())
            )
        ).thenReturn(Optional.of(savedIdempotency.get()));
        when(
            tradeRepository.findByClassroomIdAndId(command.classroomId(), first.tradeId())
        ).thenReturn(Optional.of(savedTrade.get()));

        TradeCommandResult replay = service.create(command);

        assertEquals(first.tradeId(), replay.tradeId());
        verify(tradeRepository, times(1)).saveAndFlush(any());
        verify(moneyLegRepository, times(1)).save(any());
    }

    @Test
    void rejectsAnEquippedItemBeforeCreatingATradeLock() {
        UUID itemId = UUID.randomUUID();
        UUID proposerId = UUID.fromString(command.studentSubject());
        InventoryItem item = InventoryItem.acquire(
            itemId,
            command.classroomId(),
            proposerId,
            UUID.randomUUID(),
            null,
            NOW
        );
        CreateTradeCommand itemTrade = new CreateTradeCommand(
            command.studentSubject(),
            command.classroomId(),
            command.requestId(),
            command.correlationId(),
            command.expectedStateVersion(),
            command.counterpartStudentId(),
            0,
            0,
            List.of(itemId),
            List.of(),
            "equipped item"
        );
        when(inventoryRepository.findAllForUpdate(command.classroomId(), List.of(itemId)))
            .thenReturn(List.of(item));
        when(equipmentRepository.existsByClassroomIdAndInventoryItemIdIn(
            command.classroomId(),
            List.of(itemId)
        )).thenReturn(true);

        ClassroomOperationException exception = assertThrows(
            ClassroomOperationException.class,
            () -> service.create(itemTrade)
        );

        assertEquals("ITEM_LOCKED", exception.getCode());
    }

    @Test
    void rejectsProposalWhenAmountAndTaxExceedCurrentWalletBalance() {
        proposerWallet.post(-500, NOW.minusSeconds(1));

        ClassroomOperationException exception = assertThrows(
            ClassroomOperationException.class,
            () -> service.create(command)
        );

        assertEquals("INSUFFICIENT_BALANCE", exception.getCode());
        verify(tradeRepository, never()).saveAndFlush(any());
        verify(moneyLegRepository, never()).save(any());
    }

    @Test
    void preventsCounterpartAcceptanceWhenEitherStudentCanNoLongerPay() {
        UUID proposerId = UUID.fromString(command.studentSubject());
        UUID tradeId = UUID.randomUUID();
        Trade trade = Trade.propose(
            tradeId,
            command.classroomId(),
            proposerId,
            command.counterpartStudentId(),
            command.description(),
            NOW.plusSeconds(3_600),
            NOW.minusSeconds(10)
        );
        TradeMoneyLeg moneyLeg = TradeMoneyLeg.create(
            UUID.randomUUID(),
            command.classroomId(),
            tradeId,
            proposerId,
            command.counterpartStudentId(),
            command.offeredAmount(),
            NOW.minusSeconds(10)
        );
        proposerWallet.post(-500, NOW.minusSeconds(1));
        when(studentRepository.findById(command.counterpartStudentId()))
            .thenReturn(Optional.of(counterpartStudent));
        when(tradeRepository.findForUpdate(command.classroomId(), tradeId))
            .thenReturn(Optional.of(trade));
        when(moneyLegRepository.findAllByTradeIdOrderByFromStudentId(tradeId))
            .thenReturn(List.of(moneyLeg));
        CounterpartTradeDecisionRequest accept = new CounterpartTradeDecisionRequest(
            command.counterpartStudentId().toString(),
            command.classroomId(),
            tradeId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            trade.getStateVersion(),
            CounterpartTradeDecision.ACCEPT,
            "동의합니다."
        );

        ClassroomOperationException exception = assertThrows(
            ClassroomOperationException.class,
            () -> service.decideByCounterpart(accept)
        );

        assertEquals("INSUFFICIENT_BALANCE", exception.getCode());
        assertEquals(TradeStatus.AWAITING_COUNTERPART, trade.getStatus());
        verify(tradeRepository, never()).saveAndFlush(trade);
    }
}
