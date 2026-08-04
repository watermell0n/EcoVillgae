package com.b201.ecovillage.identity.security;

import java.io.IOException;
import java.security.Principal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.b201.ecovillage.common.api.ApiError;
import com.b201.ecovillage.common.api.ApiErrorResponse;
import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.application.TeacherSessionRecord;
import com.b201.ecovillage.identity.application.TeacherSessionStore;
import com.b201.ecovillage.identity.domain.AccountStatus;
import com.b201.ecovillage.identity.domain.StudentSession;
import com.b201.ecovillage.identity.domain.StudentSessionStatus;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.domain.UserType;
import com.b201.ecovillage.identity.repository.StudentCredentialRepository;
import com.b201.ecovillage.identity.repository.StudentSessionRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

import tools.jackson.databind.ObjectMapper;

@Component
public class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final HmacAccessTokenCodec tokenCodec;
    private final TeacherSessionStore teacherSessionStore;
    private final UserAccountRepository userAccountRepository;
    private final StudentSessionRepository studentSessionRepository;
    private final StudentCredentialRepository studentCredentialRepository;
    private final ObjectMapper objectMapper;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public AccessTokenAuthenticationFilter(
        HmacAccessTokenCodec tokenCodec,
        TeacherSessionStore teacherSessionStore,
        UserAccountRepository userAccountRepository,
        StudentSessionRepository studentSessionRepository,
        StudentCredentialRepository studentCredentialRepository,
        ObjectMapper objectMapper,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.tokenCodec = tokenCodec;
        this.teacherSessionStore = teacherSessionStore;
        this.userAccountRepository = userAccountRepository;
        this.studentSessionRepository = studentSessionRepository;
        this.studentCredentialRepository = studentCredentialRepository;
        this.objectMapper = objectMapper;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            reject(request, response);
            return;
        }

        try {
            AccessTokenClaims claims = tokenCodec.decode(
                authorization.substring(BEARER_PREFIX.length()).trim()
            );
            EcoVillagePrincipal principal = authenticate(claims);
            filterChain.doFilter(new PrincipalRequest(request, principal), response);
        } catch (InvalidAccessTokenException exception) {
            reject(request, response);
        } catch (DataAccessException exception) {
            unavailable(request, response);
        }
    }

    private EcoVillagePrincipal authenticate(AccessTokenClaims claims) {
        return switch (claims.role()) {
            case "TCH" -> authenticateTeacher(claims);
            case "STU" -> authenticateStudent(claims);
            default -> throw new InvalidAccessTokenException();
        };
    }

    private EcoVillagePrincipal authenticateTeacher(AccessTokenClaims claims) {
        TeacherSessionRecord session = teacherSessionStore
            .findBySessionId(claims.sessionId())
            .filter(candidate -> candidate.isActiveAt(clock.instant()))
            .orElseThrow(InvalidAccessTokenException::new);
        UserAccount teacher = userAccountRepository
            .findById(claims.userId())
            .filter(candidate -> candidate.getStatus() == AccountStatus.ACTIVE)
            .filter(candidate -> candidate.getUserType() == UserType.TEACHER)
            .orElseThrow(InvalidAccessTokenException::new);
        if (
            !teacher.getId().toString().equals(claims.subject())
                || !session.userId().equals(teacher.getId())
                || !session.externalSubject().equals(teacher.getExternalSubject())
        ) {
            throw new InvalidAccessTokenException();
        }
        return new EcoVillagePrincipal(
            teacher.getExternalSubject(),
            claims.sessionId(),
            teacher.getId(),
            "TCH",
            null,
            claims.expiresAt()
        );
    }

    private EcoVillagePrincipal authenticateStudent(AccessTokenClaims claims) {
        UUID studentId;
        try {
            studentId = UUID.fromString(claims.subject());
        } catch (IllegalArgumentException exception) {
            throw new InvalidAccessTokenException(exception);
        }
        StudentSession session = studentSessionRepository
            .findById(claims.sessionId())
            .filter(candidate -> candidate.getStatus() == StudentSessionStatus.ACTIVE)
            .filter(candidate -> candidate.getExpiresAt().isAfter(clock.instant()))
            .orElseThrow(InvalidAccessTokenException::new);
        boolean identityMatches =
            session.getClassroomStudentId().equals(studentId)
                && session.getClassroomId().equals(claims.classroomId());
        boolean credentialMatches = studentCredentialRepository
            .findByClassroomIdAndClassroomStudentId(claims.classroomId(), studentId)
            .map(credential -> credential.getCredentialVersion() == claims.credentialVersion())
            .orElse(false);
        if (!identityMatches || !credentialMatches) {
            throw new InvalidAccessTokenException();
        }
        return new EcoVillagePrincipal(
            studentId.toString(),
            claims.sessionId(),
            studentId,
            "STU",
            claims.classroomId(),
            claims.expiresAt()
        );
    }

    private void reject(
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException {
        writeError(
            request,
            response,
            HttpStatus.UNAUTHORIZED,
            "INVALID_ACCESS_TOKEN",
            "인증 토큰이 유효하지 않거나 만료되었습니다.",
            false
        );
    }

    private void unavailable(
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException {
        writeError(
            request,
            response,
            HttpStatus.SERVICE_UNAVAILABLE,
            "AUTH_SESSION_STORE_UNAVAILABLE",
            "인증 세션을 확인할 수 없습니다. 잠시 후 다시 시도해주세요.",
            true
        );
    }

    private void writeError(
        HttpServletRequest request,
        HttpServletResponse response,
        HttpStatus status,
        String code,
        String message,
        boolean retryable
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiErrorResponse body = new ApiErrorResponse(
            new ApiError(
                code,
                message,
                retryable,
                List.of()
            ),
            new ApiMeta(
                headerUuidOrNew(request, "Idempotency-Key"),
                headerUuidOrNew(request, "X-Correlation-Id"),
                clock.instant()
            )
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private UUID headerUuidOrNew(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value != null) {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                // Invalid untrusted headers are never reflected into the response.
            }
        }
        return uuidGenerator.generate();
    }

    private static final class PrincipalRequest extends HttpServletRequestWrapper {

        private final EcoVillagePrincipal principal;

        private PrincipalRequest(
            HttpServletRequest request,
            EcoVillagePrincipal principal
        ) {
            super(request);
            this.principal = principal;
        }

        @Override
        public Principal getUserPrincipal() {
            return principal;
        }

        @Override
        public String getRemoteUser() {
            return principal.getName();
        }

        @Override
        public boolean isUserInRole(String role) {
            return principal.role().equals(role);
        }
    }
}
