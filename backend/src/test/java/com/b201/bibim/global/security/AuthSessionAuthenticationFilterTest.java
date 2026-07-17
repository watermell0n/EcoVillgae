package com.b201.bibim.global.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AuthSessionAuthenticationFilterTest {

    @Mock AuthSessionService authSessionService;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain filterChain;
    @InjectMocks AuthSessionAuthenticationFilter filter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void studentBearerTokenTakesPriorityOverSharedCookie() throws Exception {
        UUID studentId = UUID.randomUUID();
        when(request.getRequestURI()).thenReturn("/api/v1/student/me");
        when(request.getHeader("Authorization")).thenReturn("Bearer tab-student-token");
        lenient().when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie(AuthCookieService.STUDENT_COOKIE_NAME, "shared-cookie-token")
        });
        when(authSessionService.authenticate("tab-student-token", AuthRole.STUDENT))
                .thenReturn(Optional.of(new AuthenticatedPrincipal(studentId, AuthRole.STUDENT)));

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(
                studentId,
                ((AuthenticatedPrincipal) SecurityContextHolder.getContext()
                        .getAuthentication().getPrincipal()).id()
        );
        verify(authSessionService).authenticate("tab-student-token", AuthRole.STUDENT);
        verify(authSessionService, never()).authenticate("shared-cookie-token", AuthRole.STUDENT);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidWindowTokenDoesNotFallBackToAnotherStudentsCookie() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/student/me");
        when(request.getHeader("Authorization")).thenReturn("Bearer expired-window-token");
        lenient().when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie(AuthCookieService.STUDENT_COOKIE_NAME, "other-student-cookie")
        });
        when(authSessionService.authenticate("expired-window-token", AuthRole.STUDENT))
                .thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        verify(authSessionService).authenticate("expired-window-token", AuthRole.STUDENT);
        verify(authSessionService, never()).authenticate("other-student-cookie", AuthRole.STUDENT);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void studentRequestWithoutWindowTokenDoesNotUseSharedCookie() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/student/me");
        when(request.getHeader("Authorization")).thenReturn(null);
        lenient().when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie(AuthCookieService.STUDENT_COOKIE_NAME, "shared-student-cookie")
        });

        filter.doFilterInternal(request, response, filterChain);

        verify(authSessionService, never()).authenticate("shared-student-cookie", AuthRole.STUDENT);
        verify(filterChain).doFilter(request, response);
    }
}
