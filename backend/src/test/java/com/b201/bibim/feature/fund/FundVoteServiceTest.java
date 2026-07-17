package com.b201.bibim.feature.fund;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

import java.util.Optional;
import java.util.UUID;

import com.b201.bibim.domain.finance.AccountEntity;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.finance.FinancialEntryRepository;
import com.b201.bibim.domain.finance.FinancialTransactionRepository;
import com.b201.bibim.domain.fund.FundCostOptionEntity;
import com.b201.bibim.domain.fund.FundCostOptionRepository;
import com.b201.bibim.domain.fund.FundExpenseRepository;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.settlement.FinancialSettlementItemRepository;
import com.b201.bibim.domain.settlement.SettlementItemRepository;
import com.b201.bibim.domain.settlement.SettlementRunRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.domain.vote.VoteOptionEntity;
import com.b201.bibim.domain.vote.VoteOptionRepository;
import com.b201.bibim.domain.vote.VoteRepository;
import com.b201.bibim.domain.vote.VoteSessionEntity;
import com.b201.bibim.domain.vote.VoteSessionRepository;
import com.b201.bibim.feature.fund.FundVoteDto.CastVoteRequest;
import com.b201.bibim.feature.fund.FundVoteDto.CreateVoteRequest;
import com.b201.bibim.feature.fund.FundVoteDto.VoteOptionRequest;
import com.b201.bibim.global.exception.ApiException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundVoteServiceTest {

    @Mock RoomRepository roomRepository;
    @Mock StudentRepository studentRepository;
    @Mock DaySessionRepository sessionRepository;
    @Mock AccountRepository accountRepository;
    @Mock FinancialTransactionRepository transactionRepository;
    @Mock FinancialEntryRepository entryRepository;
    @Mock FundCostOptionRepository costOptionRepository;
    @Mock FundExpenseRepository expenseRepository;
    @Mock VoteSessionRepository voteSessionRepository;
    @Mock VoteOptionRepository voteOptionRepository;
    @Mock VoteRepository voteRepository;
    @Mock SettlementRunRepository settlementRunRepository;
    @Mock SettlementItemRepository settlementItemRepository;
    @Mock FinancialSettlementItemRepository financialItemRepository;
    @InjectMocks FundVoteService service;

    @Test
    void rejectsDuplicateVoteFromSameStudent() {
        UUID studentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID voteId = UUID.randomUUID();
        UUID optionId = UUID.randomUUID();
        StudentEntity student = org.mockito.Mockito.mock(StudentEntity.class);
        RoomEntity room = org.mockito.Mockito.mock(RoomEntity.class);
        VoteSessionEntity vote = org.mockito.Mockito.mock(VoteSessionEntity.class);
        VoteOptionEntity option = org.mockito.Mockito.mock(VoteOptionEntity.class);
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(student.getStatus()).thenReturn("ACTIVE");
        when(student.getRoomId()).thenReturn(roomId);
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(roomId);
        when(room.getCurrentStage()).thenReturn("DAY2_FUND_VOTE");
        when(room.getStatus()).thenReturn("DAY2_ACTIVE");
        when(voteSessionRepository.findByIdForUpdate(voteId)).thenReturn(Optional.of(vote));
        when(vote.getRoomId()).thenReturn(roomId);
        when(vote.getStatus()).thenReturn("OPEN");
        when(vote.getFundBalanceSnapshot()).thenReturn(100L);
        when(voteOptionRepository.findById(optionId)).thenReturn(Optional.of(option));
        when(option.getVoteSessionId()).thenReturn(voteId);
        when(option.getRequiredAmount()).thenReturn(40L);
        when(voteRepository.existsByVoteSessionIdAndStudentId(voteId, studentId)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () ->
                service.castVote(studentId, voteId, new CastVoteRequest(optionId))
        );

        assertEquals("VOTE_ALREADY_SUBMITTED", exception.getCode());
    }

    @Test
    void rejectsExpenseLargerThanClassFundBalance() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID optionId = UUID.randomUUID();
        RoomEntity room = org.mockito.Mockito.mock(RoomEntity.class);
        FundCostOptionEntity option = org.mockito.Mockito.mock(FundCostOptionEntity.class);
        AccountEntity fund = org.mockito.Mockito.mock(AccountEntity.class);
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getStatus()).thenReturn("DAY2_ACTIVE");
        when(room.getCurrentStage()).thenReturn("DAY2_FUND_COST");
        when(transactionRepository.findByIdempotencyKey("expense-request")).thenReturn(Optional.empty());
        when(expenseRepository.existsByRoomId(roomId)).thenReturn(false);
        when(costOptionRepository.findById(optionId)).thenReturn(Optional.of(option));
        when(option.getRoomId()).thenReturn(roomId);
        when(option.isEnabled()).thenReturn(true);
        when(option.getAmount()).thenReturn(100L);
        when(accountRepository.findRoomAccountForUpdate(roomId, "CLASS_FUND")).thenReturn(Optional.of(fund));
        when(fund.getBalance()).thenReturn(50L);

        ApiException exception = assertThrows(ApiException.class, () ->
                service.applyExpense(teacherId, roomId, "expense-request", optionId)
        );

        assertEquals("CLASS_FUND_INSUFFICIENT", exception.getCode());
    }

    @Test
    void allowsPreparingVoteWithoutExpenseWhenNoCostIsAffordable() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        RoomEntity room = org.mockito.Mockito.mock(RoomEntity.class);
        FundCostOptionEntity cost = org.mockito.Mockito.mock(FundCostOptionEntity.class);
        AccountEntity fund = org.mockito.Mockito.mock(AccountEntity.class);
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getStatus()).thenReturn("DAY2_ACTIVE");
        when(room.getCurrentStage()).thenReturn("DAY2_FUND_COST");
        when(expenseRepository.existsByRoomId(roomId)).thenReturn(false);
        when(accountRepository.findByRoomIdAndStudentIdIsNullAndAccountType(roomId, "CLASS_FUND"))
                .thenReturn(Optional.of(fund));
        when(fund.getBalance()).thenReturn(29L);
        when(costOptionRepository.findAllByRoomIdOrderByDisplayOrder(roomId)).thenReturn(java.util.List.of(cost));
        when(cost.isEnabled()).thenReturn(true);
        when(cost.getAmount()).thenReturn(30L);
        when(voteSessionRepository.findByRoomId(roomId)).thenReturn(Optional.empty());
        when(voteSessionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createVote(
                teacherId,
                roomId,
                new CreateVoteRequest(
                        "남은 공동기금은 어디에 사용할까요?",
                        java.util.List.of(
                                new VoteOptionRequest("저축", "다음 활동을 위해 남겨요.", 0),
                                new VoteOptionRequest("간식", "함께 간식을 나눠요.", 20)
                        )
                )
        );

        assertEquals("READY", response.status());
        assertEquals(29L, response.fundBalanceSnapshot());
    }

    @Test
    void rejectsClosingVoteWhileActiveStudentHasNotParticipated() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID voteId = UUID.randomUUID();
        RoomEntity room = org.mockito.Mockito.mock(RoomEntity.class);
        StudentEntity active = org.mockito.Mockito.mock(StudentEntity.class);
        VoteSessionEntity vote = org.mockito.Mockito.mock(VoteSessionEntity.class);
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getStatus()).thenReturn("DAY2_ACTIVE");
        when(room.getCurrentStage()).thenReturn("DAY2_FUND_VOTE");
        when(voteSessionRepository.findByIdForUpdate(voteId)).thenReturn(Optional.of(vote));
        when(vote.getId()).thenReturn(voteId);
        when(vote.getRoomId()).thenReturn(roomId);
        when(studentRepository.findAllByRoomIdAndStatusOrderByParticipantNumber(roomId, "ACTIVE"))
                .thenReturn(java.util.List.of(active));

        ApiException exception = assertThrows(ApiException.class, () ->
                service.closeVote(teacherId, roomId, voteId, false)
        );

        assertEquals("VOTE_INCOMPLETE", exception.getCode());
        verify(vote, never()).close();
    }

    @Test
    void forceClosesVoteWithPendingActiveStudents() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID voteId = UUID.randomUUID();
        RoomEntity room = org.mockito.Mockito.mock(RoomEntity.class);
        StudentEntity active = org.mockito.Mockito.mock(StudentEntity.class);
        VoteSessionEntity vote = org.mockito.Mockito.mock(VoteSessionEntity.class);
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getStatus()).thenReturn("DAY2_ACTIVE");
        when(room.getCurrentStage()).thenReturn("DAY2_FUND_VOTE");
        when(voteSessionRepository.findByIdForUpdate(voteId)).thenReturn(Optional.of(vote));
        when(vote.getId()).thenReturn(voteId);
        when(vote.getRoomId()).thenReturn(roomId);
        when(vote.getStatus()).thenReturn("CLOSED");
        when(studentRepository.findAllByRoomIdAndStatusOrderByParticipantNumber(roomId, "ACTIVE"))
                .thenReturn(java.util.List.of(active));

        service.closeVote(teacherId, roomId, voteId, true);

        verify(vote).close();
        verify(room).markControlStateChanged();
    }
}
