package com.b201.bibim.feature.auth.student;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.auth.student.StudentAuthDto.EnterRequest;
import com.b201.bibim.feature.auth.student.StudentAuthDto.LoginRequest;
import com.b201.bibim.feature.auth.student.StudentAuthDto.RequiredAction;
import com.b201.bibim.feature.auth.student.StudentAuthDto.SetupRequest;
import com.b201.bibim.feature.monitoring.ActivityEventService;
import com.b201.bibim.feature.flow.StudentFlowService;
import com.b201.bibim.global.security.AuthSessionService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class StudentAuthServiceTest {

    @Mock StudentRepository studentRepository;
    @Mock RoomRepository roomRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthSessionService authSessionService;
    @Mock AccountRepository accountRepository;
    @Mock ActivityEventService activityEventService;
    @Mock StudentFlowService studentFlowService;

    @InjectMocks StudentAuthService studentAuthService;

    @Test
    void existingStudentCanEnterAndLoginToCompletedRoom() {
        UUID studentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        StudentEntity student = org.mockito.Mockito.mock(StudentEntity.class);
        RoomEntity room = org.mockito.Mockito.mock(RoomEntity.class);

        when(studentRepository.findByParticipationCode("ECO-01"))
                .thenReturn(Optional.of(student));
        when(studentRepository.findByParticipationCodeForUpdate("ECO-01"))
                .thenReturn(Optional.of(student));
        when(student.getId()).thenReturn(studentId);
        when(student.getRoomId()).thenReturn(roomId);
        when(student.getStatus()).thenReturn("ACTIVE");
        when(student.getPasswordHash()).thenReturn("encoded-password");
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(roomId);
        when(room.getStatus()).thenReturn("COMPLETED");
        when(room.getCurrentStage()).thenReturn("DAY2_COMPLETED");
        when(passwordEncoder.matches("1234", "encoded-password")).thenReturn(true);
        when(authSessionService.createStudentSession(student)).thenReturn("session-token");
        when(accountRepository.findByRoomIdAndStudentIdAndAccountType(
                roomId, studentId, "STUDENT_WALLET"
        )).thenReturn(Optional.empty());

        assertEquals(
                RequiredAction.LOGIN,
                studentAuthService.enter(new EnterRequest("eco-01")).requiredAction()
        );
        StudentAuthService.LoginResult result = studentAuthService.login(
                new LoginRequest("eco-01", "1234")
        );

        assertEquals("COMPLETED", result.response().roomStatus());
        assertEquals("DAY2_COMPLETED", result.response().currentStage());
        verify(student).recordLogin();
        verify(authSessionService).createStudentSession(student);
    }

    @Test
    void unregisteredStudentCannotSetUpInCompletedRoom() {
        UUID roomId = UUID.randomUUID();
        StudentEntity student = org.mockito.Mockito.mock(StudentEntity.class);
        RoomEntity room = org.mockito.Mockito.mock(RoomEntity.class);

        when(studentRepository.findByParticipationCode("ECO-02"))
                .thenReturn(Optional.of(student));
        when(student.getRoomId()).thenReturn(roomId);
        when(student.getStatus()).thenReturn("INVITED");
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getStatus()).thenReturn("COMPLETED");

        assertEquals(
                RequiredAction.ROOM_CLOSED,
                studentAuthService.enter(new EnterRequest("eco-02")).requiredAction()
        );
    }

    @Test
    void entersRoomWithOnlyTheStudentNumberFromAClassLink() {
        RoomEntity room = RoomEntity.preparing(UUID.randomUUID(), "햇살반", 24);
        UUID roomId = room.getId();
        StudentEntity student = StudentEntity.invited(roomId, 7, "ECO-ROOM-07");
        when(studentRepository.findByRoomIdAndParticipantNumber(roomId, 7))
                .thenReturn(Optional.of(student));
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        var response = studentAuthService.enter(new EnterRequest(null, roomId, 7));

        assertEquals("ECO-ROOM-07", response.participationCode());
        assertEquals(7, response.participantNumber());
        assertEquals(RequiredAction.SETUP, response.requiredAction());
    }

    @Test
    void resetStudentKeepsNicknameAndSetsOnlyANewPassword() {
        UUID roomId = UUID.randomUUID();
        StudentEntity student = StudentEntity.invited(roomId, 2, "ECO-02");
        student.completeSetup("비빔이", "old-password");
        student.resetPassword();
        RoomEntity room = org.mockito.Mockito.mock(RoomEntity.class);

        when(studentRepository.findByParticipationCode("ECO-02")).thenReturn(Optional.of(student));
        when(studentRepository.findByParticipationCodeForUpdate("ECO-02")).thenReturn(Optional.of(student));
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(roomId);
        when(room.getName()).thenReturn("푸른반");
        when(room.getStatus()).thenReturn("ACTIVE");
        when(passwordEncoder.encode("5678")).thenReturn("new-password");
        when(authSessionService.createStudentSession(student)).thenReturn("session-token");
        when(accountRepository.findByRoomIdAndStudentIdAndAccountType(
                roomId, student.getId(), "STUDENT_WALLET"
        )).thenReturn(Optional.empty());

        assertEquals(
                RequiredAction.RESET_PASSWORD,
                studentAuthService.enter(new EnterRequest("eco-02")).requiredAction()
        );
        StudentAuthService.LoginResult result = studentAuthService.setup(
                new SetupRequest("eco-02", null, "5678")
        );

        assertEquals("비빔이", result.response().nickname());
        assertEquals("new-password", student.getPasswordHash());
        assertEquals("ACTIVE", student.getStatus());
        verify(studentFlowService, never()).initialize(any(), any());
    }
}
