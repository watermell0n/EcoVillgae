package com.b201.bibim.feature.auth.student;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.b201.bibim.feature.auth.student.StudentAuthDto.LoginRequest;
import com.b201.bibim.feature.auth.student.StudentAuthDto.StudentResponse;
import com.b201.bibim.global.security.AuthSessionAuthenticationFilter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

@ExtendWith(MockitoExtension.class)
class StudentAuthControllerTest {

    @Mock StudentAuthService studentAuthService;
    @Mock HttpServletRequest request;
    @InjectMocks StudentAuthController controller;

    @Test
    void returnsWindowTokenWithoutWritingSharedStudentCookie() {
        LoginRequest login = new LoginRequest("ECO-01", "1234");
        StudentResponse student = new StudentResponse(
                UUID.randomUUID(), UUID.randomUUID(), "경제교실", 1, "학생1",
                "DAY1_ACTIVE", 1, "DAY1_LOBBY", "GUIDED", 100, 1, "CONNECTED"
        );
        when(studentAuthService.login(login))
                .thenReturn(new StudentAuthService.LoginResult(student, "window-token-1"));

        var response = controller.login(login, request);

        assertEquals(
                "window-token-1",
                response.getHeaders().getFirst(
                        AuthSessionAuthenticationFilter.STUDENT_SESSION_HEADER
                )
        );
        assertNull(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        assertEquals(student, response.getBody().data());
    }
}
