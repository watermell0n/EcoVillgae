package com.b201.bibim.feature.auth.student;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.b201.bibim.feature.monitoring.ActivityEventService;
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
}
