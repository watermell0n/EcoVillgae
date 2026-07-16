package com.b201.bibim.feature.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.room.RoomCommandRequestRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.room.RoomStage;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.domain.settlement.SettlementRunRepository;
import com.b201.bibim.feature.room.RoomSessionDto.CommandRequest;
import com.b201.bibim.global.exception.ApiException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class RoomSessionServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomCommandRequestRepository commandRequestRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SettlementRunRepository settlementRunRepository;

    @Mock
    private RoomEntity room;

    @InjectMocks
    private RoomSessionService roomSessionService;

    @Test
    void rejectsNonAdjacentStageWithStateConflict() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getVersion()).thenReturn(3);
        when(room.getCurrentStage()).thenReturn("DAY1_LOBBY");

        ApiException exception = assertThrows(ApiException.class, () -> roomSessionService.command(
                teacherId,
                roomId,
                "request-1",
                new CommandRequest("SET_STAGE", "DAY1_PAYSLIP_GUIDED", 3)
        ));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("STATE_CONFLICT", exception.getCode());
        verify(room, never()).changeStage(RoomStage.DAY1_PAYSLIP_GUIDED);
    }

    @Test
    void rejectsStaleExpectedVersionWithStateConflict() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getVersion()).thenReturn(4);

        ApiException exception = assertThrows(ApiException.class, () -> roomSessionService.command(
                teacherId,
                roomId,
                "request-2",
                new CommandRequest("SET_STAGE", "DAY1_PROFILE", 3)
        ));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("STATE_CONFLICT", exception.getCode());
        verify(room, never()).changeStage(RoomStage.DAY1_PROFILE);
    }

    @Test
    void rejectsPayslipStageUntilIncomeSettlementCompletes() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getVersion()).thenReturn(5);
        when(room.getCurrentStage()).thenReturn("DAY1_INCOME_SETTLEMENT");
        when(commandRequestRepository.findByRoomIdAndIdempotencyKey(roomId, "request-3"))
                .thenReturn(Optional.empty());
        when(settlementRunRepository.existsByRoomIdAndSettlementTypeAndStatus(
                roomId,
                "DAY1_INCOME",
                "COMPLETED"
        )).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () -> roomSessionService.command(
                teacherId,
                roomId,
                "request-3",
                new CommandRequest("SET_STAGE", "DAY1_PAYSLIP_GUIDED", 5)
        ));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("STATE_CONFLICT", exception.getCode());
        verify(room, never()).changeStage(RoomStage.DAY1_PAYSLIP_GUIDED);
    }

    @Test
    void rejectsReturningToQuizAfterIncomeSettlementCompletes() {
        UUID teacherId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(room.getTeacherId()).thenReturn(teacherId);
        when(room.getVersion()).thenReturn(6);
        when(room.getCurrentStage()).thenReturn("DAY1_INCOME_SETTLEMENT");
        when(commandRequestRepository.findByRoomIdAndIdempotencyKey(roomId, "request-4"))
                .thenReturn(Optional.empty());
        when(settlementRunRepository.existsByRoomIdAndSettlementTypeAndStatus(
                roomId,
                "DAY1_INCOME",
                "COMPLETED"
        )).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> roomSessionService.command(
                teacherId,
                roomId,
                "request-4",
                new CommandRequest("SET_STAGE", "DAY1_QUIZ", 6)
        ));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("STATE_CONFLICT", exception.getCode());
        verify(room, never()).changeStage(RoomStage.DAY1_QUIZ);
    }
}
