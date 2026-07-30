package com.b201.ecovillage.trade.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.trade.domain.Trade;
import com.b201.ecovillage.trade.repository.InventoryItemRepository;
import com.b201.ecovillage.trade.repository.TradeDecisionRepository;
import com.b201.ecovillage.trade.repository.TradeItemLegRepository;
import com.b201.ecovillage.trade.repository.TradeMoneyLegRepository;
import com.b201.ecovillage.trade.repository.TradeRepository;

class TradeCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    private TradeRepository tradeRepository;
    private TradeMoneyLegRepository moneyLegRepository;
    private IdempotencyRecordRepository idempotencyRepository;
    private TradeCommandService service;
    private CreateTradeCommand command;
    private AtomicReference<Trade> savedTrade;
    private AtomicReference<IdempotencyRecord> savedIdempotency;

    @BeforeEach
    void setUp() {
        ClassroomAuthorizationService authorizationService =
            mock(ClassroomAuthorizationService.class);
        ClassroomRepository classroomRepository = mock(ClassroomRepository.class);
        ClassroomStudentRepository studentRepository =
            mock(ClassroomStudentRepository.class);
        tradeRepository = mock(TradeRepository.class);
        moneyLegRepository = mock(TradeMoneyLegRepository.class);
        TradeItemLegRepository itemLegRepository = mock(TradeItemLegRepository.class);
        TradeDecisionRepository decisionRepository = mock(TradeDecisionRepository.class);
        InventoryItemRepository inventoryRepository = mock(InventoryItemRepository.class);
        idempotencyRepository = mock(IdempotencyRecordRepository.class);

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
            idempotencyRepository,
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
        ClassroomStudent counterpart = ClassroomStudent.enroll(
            counterpartId,
            classroomId,
            2,
            "counterpart",
            NOW
        );

        when(classroomRepository.findById(classroomId))
            .thenReturn(Optional.of(Classroom.active(classroomId, "ABCD1234", NOW)));
        when(
            studentRepository.findAllForUpdate(
                eq(classroomId),
                any(),
                eq(ClassroomStudentStatus.ACTIVE)
            )
        ).thenReturn(List.of(proposer, counterpart));
        when(
            idempotencyRepository.findForReplay(
                eq(classroomId),
                eq(proposerId),
                any(),
                eq(requestId.toString())
            )
        ).thenReturn(Optional.empty());
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
}
