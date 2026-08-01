package com.b201.ecovillage.identity.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.identity.domain.StudentCredential;
import com.b201.ecovillage.identity.domain.StudentSession;
import com.b201.ecovillage.identity.domain.StudentSessionStatus;
import com.b201.ecovillage.identity.repository.StudentCredentialRepository;
import com.b201.ecovillage.identity.repository.StudentSessionRepository;
import com.b201.ecovillage.identity.security.IdentitySecretHasher;
import com.b201.ecovillage.identity.security.IssuedStudentTokens;
import com.b201.ecovillage.identity.security.IssuedTeacherTokens;
import com.b201.ecovillage.identity.security.StudentTokenIssuer;
import com.b201.ecovillage.identity.security.TeacherTokenIssuer;

@Service
public class SessionRefreshService {

    private final IdentitySecretHasher secretHasher;
    private final TeacherSessionStore teacherSessionStore;
    private final TeacherTokenIssuer teacherTokenIssuer;
    private final StudentSessionRepository studentSessionRepository;
    private final StudentCredentialRepository studentCredentialRepository;
    private final StudentTokenIssuer studentTokenIssuer;
    private final Clock clock;

    public SessionRefreshService(
        IdentitySecretHasher secretHasher,
        TeacherSessionStore teacherSessionStore,
        TeacherTokenIssuer teacherTokenIssuer,
        StudentSessionRepository studentSessionRepository,
        StudentCredentialRepository studentCredentialRepository,
        StudentTokenIssuer studentTokenIssuer,
        Clock clock
    ) {
        this.secretHasher = secretHasher;
        this.teacherSessionStore = teacherSessionStore;
        this.teacherTokenIssuer = teacherTokenIssuer;
        this.studentSessionRepository = studentSessionRepository;
        this.studentCredentialRepository = studentCredentialRepository;
        this.studentTokenIssuer = studentTokenIssuer;
        this.clock = clock;
    }

    @Transactional
    public SessionRefreshResult refreshStudent(String refreshToken) {
        String normalizedToken = requireRefreshToken(refreshToken);
        if (normalizedToken.startsWith("tch_")) {
            throw invalidRefreshToken();
        }
        String tokenHash = secretHasher.hashRefreshToken(normalizedToken);
        return refreshStudent(
            studentSessionRepository
                .findForRefreshByTokenHash(tokenHash)
                .orElseThrow(SessionRefreshService::invalidRefreshToken)
        );
    }

    @Transactional
    public SessionRefreshResult refreshTeacher(String refreshToken) {
        String normalizedToken = requireRefreshToken(refreshToken);
        if (normalizedToken.startsWith("stu_")) {
            throw invalidRefreshToken();
        }
        return refreshTeacherFromStore(secretHasher.hashRefreshToken(normalizedToken));
    }

    private static String requireRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw invalidRefreshToken();
        }
        return refreshToken.trim();
    }

    private SessionRefreshResult refreshTeacherFromStore(String tokenHash) {
        try {
            return teacherSessionStore
                .findByRefreshTokenHash(tokenHash)
                .map(session -> refreshTeacher(session, tokenHash))
                .orElseThrow(SessionRefreshService::invalidRefreshToken);
        } catch (DataAccessException exception) {
            throw sessionStoreUnavailable();
        }
    }

    private SessionRefreshResult refreshTeacher(
        TeacherSessionRecord session,
        String currentTokenHash
    ) {
        Instant now = clock.instant();
        if (!session.isActiveAt(now)) {
            throw invalidRefreshToken();
        }
        IssuedTeacherTokens tokens = teacherTokenIssuer.issue(
            session.sessionId(),
            session.userId(),
            now
        );
        boolean rotated = teacherSessionStore.rotate(
            session.sessionId(),
            currentTokenHash,
            tokens.refreshTokenHash(),
            tokens.refreshTokenExpiresAt(),
            now
        );
        if (!rotated) {
            throw invalidRefreshToken();
        }
        return new SessionRefreshResult(
            tokens.accessToken(),
            tokens.refreshToken(),
            tokens.accessTokenExpiresAt(),
            tokens.refreshTokenExpiresAt(),
            session.sessionId(),
            "TCH"
        );
    }

    private SessionRefreshResult refreshStudent(StudentSession session) {
        Instant now = clock.instant();
        if (
            session.getStatus() != StudentSessionStatus.ACTIVE
                || !session.getExpiresAt().isAfter(now)
        ) {
            throw invalidRefreshToken();
        }
        StudentCredential credential = studentCredentialRepository
            .findByClassroomIdAndClassroomStudentId(
                session.getClassroomId(),
                session.getClassroomStudentId()
            )
            .orElseThrow(SessionRefreshService::invalidRefreshToken);
        IssuedStudentTokens tokens = studentTokenIssuer.issue(
            session.getId(),
            session.getClassroomId(),
            session.getClassroomStudentId(),
            credential.getCredentialVersion(),
            now
        );
        session.rotate(tokens.refreshTokenHash(), tokens.refreshTokenExpiresAt(), now);
        studentSessionRepository.saveAndFlush(session);
        return new SessionRefreshResult(
            tokens.accessToken(),
            tokens.refreshToken(),
            tokens.accessTokenExpiresAt(),
            tokens.refreshTokenExpiresAt(),
            session.getId(),
            "STU"
        );
    }

    private static TeacherAuthenticationException invalidRefreshToken() {
        return new TeacherAuthenticationException(
            HttpStatus.UNAUTHORIZED,
            "INVALID_REFRESH_TOKEN",
            "갱신 토큰이 유효하지 않거나 이미 사용되었습니다.",
            false
        );
    }

    private static TeacherAuthenticationException sessionStoreUnavailable() {
        return new TeacherAuthenticationException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "AUTH_SESSION_STORE_UNAVAILABLE",
            "인증 세션 저장소에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.",
            true
        );
    }
}
