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
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthSessionAuthenticationFilter extends OncePerRequestFilter {

    public static final String STUDENT_SESSION_HEADER = "X-Student-Session-Token";

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
            AuthRole expectedRole = expectedRole(request.getRequestURI());
            String cookieName = switch (expectedRole) {
                case STUDENT -> AuthCookieService.STUDENT_COOKIE_NAME;
                case ADMIN -> AuthCookieService.ADMIN_COOKIE_NAME;
                case TEACHER -> AuthCookieService.TEACHER_COOKIE_NAME;
            };

            findToken(request, expectedRole, cookieName)
                    .flatMap(token -> authSessionService.authenticate(token, expectedRole))
                    .ifPresent(this::setAuthentication);
        }

        filterChain.doFilter(request, response);
    }

    private AuthRole expectedRole(String requestUri) {
        if (requestUri.startsWith("/api/v1/student/")) {
            return AuthRole.STUDENT;
        }
        if (requestUri.startsWith("/api/v1/admin/")) {
            return AuthRole.ADMIN;
        }
        return AuthRole.TEACHER;
    }

    private java.util.Optional<String> findToken(
            HttpServletRequest request,
            AuthRole expectedRole,
            String cookieName
    ) {
        if (expectedRole == AuthRole.STUDENT) {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            return authorization == null
                    ? java.util.Optional.empty()
                    : bearerToken(authorization);
        }
        return findCookie(request, cookieName);
    }

    private java.util.Optional<String> bearerToken(String authorization) {
        String prefix = "Bearer ";
        if (!authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return java.util.Optional.empty();
        }
        String token = authorization.substring(prefix.length()).trim();
        return token.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(token);
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
