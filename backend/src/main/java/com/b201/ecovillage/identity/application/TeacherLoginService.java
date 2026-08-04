package com.b201.ecovillage.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.domain.AccountStatus;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.domain.UserType;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.identity.security.IssuedTeacherTokens;
import com.b201.ecovillage.identity.security.TeacherTokenIssuer;

@Service
public class TeacherLoginService {

    private final TeacherIdentityVerifier identityVerifier;
    private final UserAccountRepository userAccountRepository;
    private final TeacherSessionStore sessionStore;
    private final TeacherTokenIssuer tokenIssuer;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public TeacherLoginService(
        TeacherIdentityVerifier identityVerifier,
        UserAccountRepository userAccountRepository,
        TeacherSessionStore sessionStore,
        TeacherTokenIssuer tokenIssuer,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.identityVerifier = identityVerifier;
        this.userAccountRepository = userAccountRepository;
        this.sessionStore = sessionStore;
        this.tokenIssuer = tokenIssuer;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TeacherLoginResult login(String idToken) {
        VerifiedTeacherIdentity identity = identityVerifier.verify(idToken);
        UserAccount teacher = userAccountRepository
            .findByExternalSubject(identity.externalSubject())
            .orElseThrow(() -> new TeacherAuthenticationException(
                HttpStatus.FORBIDDEN,
                "TEACHER_NOT_REGISTERED",
                "등록된 이코빌리지 교사 계정을 찾을 수 없습니다.",
                false
            ));
        return createSession(teacher);
    }

    @Transactional
    public TeacherLoginResult createSession(UserAccount teacher) {
        if (
            teacher.getUserType() != UserType.TEACHER
                || teacher.getStatus() != AccountStatus.ACTIVE
        ) {
            throw new TeacherAuthenticationException(
                HttpStatus.FORBIDDEN,
                "ACCOUNT_INACTIVE",
                "사용할 수 없는 교사 계정입니다.",
                false
            );
        }

        Instant now = clock.instant();
        UUID sessionId = uuidGenerator.generate();
        IssuedTeacherTokens tokens = tokenIssuer.issue(
            sessionId,
            teacher.getId(),
            now
        );
        try {
            sessionStore.save(
                new TeacherSessionRecord(
                    sessionId,
                    teacher.getId(),
                    teacher.getExternalSubject(),
                    tokens.refreshTokenHash(),
                    TeacherSessionStatus.ACTIVE,
                    now,
                    tokens.refreshTokenExpiresAt(),
                    null
                )
            );
        } catch (DataAccessException exception) {
            throw sessionStoreUnavailable();
        }

        return new TeacherLoginResult(
            tokens.accessToken(),
            tokens.refreshToken(),
            tokens.accessTokenExpiresAt(),
            tokens.refreshTokenExpiresAt(),
            sessionId,
            teacher.getId(),
            teacher.getDisplayName()
        );
    }

    private static TeacherAuthenticationException sessionStoreUnavailable() {
        return new TeacherAuthenticationException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "AUTH_SESSION_STORE_UNAVAILABLE",
            "교사 세션을 만들 수 없습니다. 잠시 후 다시 시도해주세요.",
            true
        );
    }
}
