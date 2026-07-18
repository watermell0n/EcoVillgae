package com.b201.bibim.feature.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.b201.bibim.domain.auth.AuthSessionRepository;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.monitoring.ActivityEventRepository;
import com.b201.bibim.domain.monitoring.StudentPresenceEntity;
import com.b201.bibim.domain.monitoring.StudentPresenceRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentActivityProgressRepository;
import com.b201.bibim.domain.student.StudentFlowStateRepository;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.admin.AdminMonitoringDto.RoomStudentsResponse;
import com.b201.bibim.feature.admin.AdminMonitoringDto.SessionTerminationResponse;
import com.b201.bibim.feature.monitoring.ActivityEventService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminMonitoringServiceTest {

    @Mock
    private RoomRepository roomRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private StudentPresenceRepository presenceRepository;
    @Mock
    private ActivityEventRepository activityEventRepository;
    @Mock
    private ActivityEventService activityEventService;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AuthSessionRepository authSessionRepository;
    @Mock
    private StudentFlowStateRepository flowStateRepository;
    @Mock
    private StudentActivityProgressRepository progressRepository;
    @Mock
    private RoomEntity room;
    @Mock
    private StudentEntity student;
    @Mock
    private StudentPresenceEntity presence;

    @InjectMocks
    private AdminMonitoringService adminMonitoringService;

    @Test
    void reportsForcedPresenceAsOffline() {
        UUID roomId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        prepareRoomAndStudent(roomId, studentId);
        when(presenceRepository.findAllByRoomId(roomId)).thenReturn(List.of(presence));
        when(presence.getStudentId()).thenReturn(studentId);
        when(presence.getForcedOfflineAt()).thenReturn(Instant.now());
        when(presence.getClientSyncVersion()).thenReturn(3L);
        when(presence.getCurrentPage()).thenReturn("SESSION_TERMINATED");
        when(presence.getReportedStage()).thenReturn("DAY1_QUIZ");
        when(accountRepository.findByRoomIdAndStudentIdAndAccountType(
                roomId,
                studentId,
                "STUDENT_WALLET"
        )).thenReturn(Optional.empty());

        RoomStudentsResponse response = adminMonitoringService.students(roomId);

        assertEquals("OFFLINE", response.students().getFirst().connectionStatus());
        assertEquals("SESSION_TERMINATED", response.students().getFirst().currentPage());
        assertEquals("ECO-TEST-01", response.students().getFirst().participationCode());
    }

    @Test
    void terminatesSessionAndRecordsAuditEvent() {
        UUID adminId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getCurrentStage()).thenReturn("DAY1_QUIZ");
        when(studentRepository.findByIdForUpdate(studentId)).thenReturn(Optional.of(student));
        when(student.getRoomId()).thenReturn(roomId);
        when(authSessionRepository.revokeActiveStudentSessions(
                org.mockito.ArgumentMatchers.eq(studentId),
                org.mockito.ArgumentMatchers.any(Instant.class)
        )).thenReturn(1);
        when(presenceRepository.findByStudentIdForUpdate(studentId)).thenReturn(Optional.of(presence));

        SessionTerminationResponse response = adminMonitoringService.terminateStudentSession(
                adminId,
                roomId,
                studentId,
                "중복 로그인 복구"
        );

        assertEquals(1, response.revokedSessionCount());
        verify(student).forceLogout();
        verify(presence).forceOffline(org.mockito.ArgumentMatchers.any(Instant.class));
        verify(activityEventService).record(
                org.mockito.ArgumentMatchers.eq(roomId),
                org.mockito.ArgumentMatchers.eq(studentId),
                org.mockito.ArgumentMatchers.eq("ADMIN"),
                org.mockito.ArgumentMatchers.eq(adminId),
                org.mockito.ArgumentMatchers.eq("STUDENT_SESSION_TERMINATED"),
                org.mockito.ArgumentMatchers.eq("DAY1_QUIZ"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("SESSION_TERMINATED"),
                org.mockito.ArgumentMatchers.argThat(metadata ->
                        "중복 로그인 복구".equals(metadata.get("reason"))
                )
        );
    }

    private void prepareRoomAndStudent(UUID roomId, UUID studentId) {
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(roomId);
        when(room.getName()).thenReturn("테스트 교실");
        when(room.getCurrentStage()).thenReturn("DAY1_QUIZ");
        when(room.getSyncVersion()).thenReturn(3L);
        when(studentRepository.findAllByRoomIdOrderByParticipantNumber(roomId))
                .thenReturn(List.of(student));
        when(student.getId()).thenReturn(studentId);
        when(student.getParticipantNumber()).thenReturn(1);
        when(student.getParticipationCode()).thenReturn("ECO-TEST-01");
        when(student.getStatus()).thenReturn("ACTIVE");
    }
}
