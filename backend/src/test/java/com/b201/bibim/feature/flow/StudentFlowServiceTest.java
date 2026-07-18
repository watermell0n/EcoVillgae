package com.b201.bibim.feature.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.monitoring.StudentPresenceEntity;
import com.b201.bibim.domain.monitoring.StudentPresenceRepository;
import com.b201.bibim.domain.quiz.QuizRepository;
import com.b201.bibim.domain.quiz.QuizSubmissionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.student.StudentActivityProgressEntity;
import com.b201.bibim.domain.student.StudentActivityProgressRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentFlowStateEntity;
import com.b201.bibim.domain.student.StudentFlowStateRepository;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.monitoring.ActivityEventService;
import com.b201.bibim.feature.settlement.Day2FinanceService;
import com.b201.bibim.feature.settlement.Day2QuizRewardService;
import com.b201.bibim.feature.settlement.SettlementService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentFlowServiceTest {

    @Mock StudentFlowStateRepository flowRepository;
    @Mock StudentActivityProgressRepository progressRepository;
    @Mock StudentRepository studentRepository;
    @Mock RoomRepository roomRepository;
    @Mock AccountRepository accountRepository;
    @Mock StudentPresenceRepository presenceRepository;
    @Mock QuizRepository quizRepository;
    @Mock QuizSubmissionRepository submissionRepository;
    @Mock SettlementService settlementService;
    @Mock Day2FinanceService day2FinanceService;
    @Mock Day2QuizRewardService day2QuizRewardService;
    @Mock ActivityEventService activityEventService;
    @Mock StudentEntity student;
    @Mock RoomEntity room;
    @Mock StudentPresenceEntity presence;

    @InjectMocks StudentFlowService service;

    @Test
    void keepsDisconnectedStudentOnTheMissedStage() {
        UUID roomId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        StudentFlowStateEntity state = StudentFlowStateEntity.live(
                roomId, studentId, "DAY1_KNOWLEDGE_GUIDED"
        );
        prepare(roomId, studentId, state);
        when(presenceRepository.findAllByRoomId(roomId)).thenReturn(List.of());
        when(progressRepository.findByStudentIdAndActivityCode(
                studentId, "DAY1_KNOWLEDGE_GUIDED"
        )).thenReturn(Optional.empty());
        when(progressRepository.save(any(StudentActivityProgressEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.captureStageCompletion(room, "DAY1_KNOWLEDGE_GUIDED");

        assertEquals("CATCH_UP", state.getFlowMode());
        assertEquals("DAY1_KNOWLEDGE_GUIDED", state.getEffectiveStage());
        assertEquals(8_000, state.getRewardRateBps());
    }

    @Test
    void recordsLiveCompletionWhenHeartbeatIsCurrent() {
        UUID roomId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        StudentFlowStateEntity state = StudentFlowStateEntity.live(
                roomId, studentId, "DAY1_KNOWLEDGE_GUIDED"
        );
        StudentActivityProgressEntity progress = StudentActivityProgressEntity.available(
                roomId, studentId, "DAY1_KNOWLEDGE_GUIDED", "LIVE", 10_000
        );
        prepare(roomId, studentId, state);
        when(presenceRepository.findAllByRoomId(roomId)).thenReturn(List.of(presence));
        when(presence.getStudentId()).thenReturn(studentId);
        when(presence.getReportedStage()).thenReturn("DAY1_KNOWLEDGE_GUIDED");
        when(presence.getLastSeenAt()).thenReturn(Instant.now());
        when(progressRepository.findByStudentIdAndActivityCode(
                studentId, "DAY1_KNOWLEDGE_GUIDED"
        )).thenReturn(Optional.of(progress));

        service.captureStageCompletion(room, "DAY1_KNOWLEDGE_GUIDED");

        assertEquals("LIVE", state.getFlowMode());
        assertEquals("COMPLETED", progress.getStatus());
        assertEquals(10_000, progress.getRewardRateBps());
    }

    private void prepare(
            UUID roomId, UUID studentId, StudentFlowStateEntity state
    ) {
        when(room.getId()).thenReturn(roomId);
        when(student.getId()).thenReturn(studentId);
        when(studentRepository.findAllByRoomIdAndStatusOrderByParticipantNumber(roomId, "ACTIVE"))
                .thenReturn(List.of(student));
        when(flowRepository.findByStudentIdForUpdate(studentId)).thenReturn(Optional.of(state));
    }
}
