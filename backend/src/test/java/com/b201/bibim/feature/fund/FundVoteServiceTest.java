package com.b201.bibim.feature.fund;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.b201.bibim.domain.finance.AccountEntity;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.finance.FinancialEntryRepository;
import com.b201.bibim.domain.finance.FinancialTransactionRepository;
import com.b201.bibim.domain.fund.FundCostOptionEntity;
import com.b201.bibim.domain.fund.FundCostOptionRepository;
import com.b201.bibim.domain.fund.FundExpenseRepository;
import com.b201.bibim.domain.fund.VoteEligibleStudentRepository;
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
import com.b201.bibim.domain.vote.VoteEntity;
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
    @Mock VoteEligibleStudentRepository voteEligibleStudentRepository;
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
        when(vote.getId()).thenReturn(voteId);
        when(vote.getRoomId()).thenReturn(roomId);
        when(vote.getStatus()).thenReturn("OPEN");
        when(vote.getFundBalanceSnapshot()).thenReturn(100L);
        when(voteEligibleStudentRepository.countByVoteSessionId(voteId)).thenReturn(1L);
        when(voteEligibleStudentRepository.existsByVoteSessionIdAndStudentId(voteId, studentId))
                .thenReturn(true);
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
    void allowsPreparingVoteWithoutApplyingAnExpense() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        RoomEntity room = org.mockito.Mockito.mock(RoomEntity.class);
        AccountEntity fund = org.mockito.Mockito.mock(AccountEntity.class);
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getStatus()).thenReturn("DAY2_ACTIVE");
        when(room.getCurrentStage()).thenReturn("DAY2_FUND_COST");
        when(accountRepository.findByRoomIdAndStudentIdIsNullAndAccountType(roomId, "CLASS_FUND"))
                .thenReturn(Optional.of(fund));
        when(fund.getBalance()).thenReturn(29L);
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
    void rejectsApplyingExpenseAfterVoteWasPrepared() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID optionId = UUID.randomUUID();
        RoomEntity room = org.mockito.Mockito.mock(RoomEntity.class);
        VoteSessionEntity vote = org.mockito.Mockito.mock(VoteSessionEntity.class);
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getStatus()).thenReturn("DAY2_ACTIVE");
        when(room.getCurrentStage()).thenReturn("DAY2_FUND_COST");
        when(transactionRepository.findByIdempotencyKey("expense-after-vote")).thenReturn(Optional.empty());
        when(voteSessionRepository.findByRoomId(roomId)).thenReturn(Optional.of(vote));

        ApiException exception = assertThrows(ApiException.class, () ->
                service.applyExpense(teacherId, roomId, "expense-after-vote", optionId)
        );

        assertEquals("FUND_VOTE_ALREADY_PREPARED", exception.getCode());
        verify(costOptionRepository, never()).findById(optionId);
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
        when(voteSessionRepository.findByRoomId(roomId)).thenReturn(Optional.of(vote));
        when(studentRepository.findAllByRoomIdAndStatusOrderByParticipantNumber(roomId, "ACTIVE"))
                .thenReturn(java.util.List.of(active));

        service.closeVote(teacherId, roomId, voteId, true);

        verify(vote).close();
        verify(room).markControlStateChanged();
    }

    @Test
    void hidesDecidedVoteResultFromStudentUntilTeacherOpensResultStage() {
        UUID studentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID voteId = UUID.randomUUID();
        UUID optionId = UUID.randomUUID();
        StudentEntity student = org.mockito.Mockito.mock(StudentEntity.class);
        RoomEntity room = org.mockito.Mockito.mock(RoomEntity.class);
        VoteSessionEntity voteSession = org.mockito.Mockito.mock(VoteSessionEntity.class);
        VoteOptionEntity option = org.mockito.Mockito.mock(VoteOptionEntity.class);
        VoteEntity submittedVote = org.mockito.Mockito.mock(VoteEntity.class);
        AtomicReference<String> stage = new AtomicReference<>("DAY2_FUND_VOTE");

        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(student.getId()).thenReturn(studentId);
        when(student.getStatus()).thenReturn("ACTIVE");
        when(student.getRoomId()).thenReturn(roomId);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(roomId);
        when(room.getCurrentStage()).thenAnswer(invocation -> stage.get());
        when(voteSessionRepository.findByRoomId(roomId)).thenReturn(Optional.of(voteSession));
        when(voteSession.getId()).thenReturn(voteId);
        when(voteSession.getRoomId()).thenReturn(roomId);
        when(voteSession.getStatus()).thenReturn("DECIDED");
        when(voteSession.getFundBalanceSnapshot()).thenReturn(50L);
        when(voteSession.getDecidedOptionId()).thenReturn(optionId);
        when(voteEligibleStudentRepository.findAllByVoteSessionId(voteId)).thenReturn(java.util.List.of());
        when(studentRepository.findAllByRoomIdAndStatusOrderByParticipantNumber(roomId, "ACTIVE"))
                .thenReturn(java.util.List.of(student));
        when(voteOptionRepository.findAllByVoteSessionIdOrderByDisplayOrder(voteId))
                .thenReturn(java.util.List.of(option));
        when(option.getId()).thenReturn(optionId);
        when(option.getRequiredAmount()).thenReturn(0L);
        when(voteRepository.findAllByVoteSessionId(voteId)).thenReturn(java.util.List.of(submittedVote));
        when(submittedVote.getStudentId()).thenReturn(studentId);
        when(submittedVote.getOptionId()).thenReturn(optionId);

        var hidden = service.studentVote(studentId);

        assertEquals(null, hidden.decidedOptionId());
        assertEquals(null, hidden.options().getFirst().voteCount());
        assertEquals(false, hidden.options().getFirst().decided());

        stage.set("DAY2_FUND_RESULT");
        var revealed = service.studentVote(studentId);

        assertEquals(optionId, revealed.decidedOptionId());
        assertEquals(1L, revealed.options().getFirst().voteCount());
        assertEquals(true, revealed.options().getFirst().decided());
    }

    @Test
    void rejectsStudentResultEndpointBeforeTeacherOpensResultStage() {
        UUID studentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID voteId = UUID.randomUUID();
        StudentEntity student = org.mockito.Mockito.mock(StudentEntity.class);
        RoomEntity room = org.mockito.Mockito.mock(RoomEntity.class);

        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(student.getStatus()).thenReturn("ACTIVE");
        when(student.getRoomId()).thenReturn(roomId);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getCurrentStage()).thenReturn("DAY2_FUND_VOTE");

        ApiException exception = assertThrows(ApiException.class, () ->
                service.voteResult(studentId, voteId)
        );

        assertEquals("VOTE_RESULT_NOT_OPEN", exception.getCode());
        verify(voteSessionRepository, never()).findById(voteId);
    }
}
