package com.b201.bibim.global.security;

import java.io.IOException;
import java.util.Arrays;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthSessionAuthenticationFilter extends OncePerRequestFilter {

    private final AuthSessionService authSessionService;

    public AuthSessionAuthenticationFilter(AuthSessionService authSessionService) {
        this.authSessionService = authSessionService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            AuthRole expectedRole = request.getRequestURI().startsWith("/api/v1/student/")
                    ? AuthRole.STUDENT
                    : AuthRole.TEACHER;
            String cookieName = expectedRole == AuthRole.STUDENT
                    ? AuthCookieService.STUDENT_COOKIE_NAME
                    : AuthCookieService.TEACHER_COOKIE_NAME;

            findCookie(request, cookieName)
                    .flatMap(token -> authSessionService.authenticate(token, expectedRole))
                    .ifPresent(this::setAuthentication);
        }

        filterChain.doFilter(request, response);
    }

    private java.util.Optional<String> findCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return java.util.Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> cookie.getName().equals(name))
                .map(Cookie::getValue)
                .findFirst();
    }

    private void setAuthentication(AuthenticatedPrincipal principal) {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + principal.role().name());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of(authority))
        );
    }
}
