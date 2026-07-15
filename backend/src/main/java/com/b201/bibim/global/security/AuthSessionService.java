package com.b201.bibim.global.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import com.b201.bibim.domain.auth.AuthSessionEntity;
import com.b201.bibim.domain.auth.AuthSessionRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.domain.user.UserEntity;
import com.b201.bibim.domain.user.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthSessionService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthSessionRepository authSessionRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final Duration teacherSessionDuration;
    private final Duration studentSessionDuration;

    public AuthSessionService(
            AuthSessionRepository authSessionRepository,
            UserRepository userRepository,
            StudentRepository studentRepository,
            @Value("${app.auth.teacher-session-hours}") long teacherSessionHours,
            @Value("${app.auth.student-session-hours}") long studentSessionHours
    ) {
        this.authSessionRepository = authSessionRepository;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.teacherSessionDuration = Duration.ofHours(teacherSessionHours);
        this.studentSessionDuration = Duration.ofHours(studentSessionHours);
    }

    @Transactional
    public String createTeacherSession(UserEntity teacher) {
        String token = newToken();
        authSessionRepository.save(AuthSessionEntity.teacher(
                hashToken(token),
                teacher.getId(),
                Instant.now().plus(teacherSessionDuration)
        ));
        return token;
    }

    @Transactional
    public String createStudentSession(StudentEntity student) {
        String token = newToken();
        authSessionRepository.save(AuthSessionEntity.student(
                hashToken(token),
                student.getId(),
                student.getLoginVersion(),
                Instant.now().plus(studentSessionDuration)
        ));
        return token;
    }

    @Transactional(readOnly = true)
    public Optional<AuthenticatedPrincipal> authenticate(String token, AuthRole expectedRole) {
        return authSessionRepository
                .findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(hashToken(token), Instant.now())
                .flatMap(session -> authenticateSession(session, expectedRole));
    }

    public Duration sessionDuration(AuthRole role) {
        return role == AuthRole.TEACHER ? teacherSessionDuration : studentSessionDuration;
    }

    private Optional<AuthenticatedPrincipal> authenticateSession(
            AuthSessionEntity session,
            AuthRole expectedRole
    ) {
        if (!session.getPrincipalType().equals(expectedRole.name())) {
            return Optional.empty();
        }

        if (expectedRole == AuthRole.TEACHER) {
            return userRepository.findById(session.getUserId())
                    .filter(user -> "TEACHER".equals(user.getRole()))
                    .filter(user -> "ACTIVE".equals(user.getStatus()))
                    .map(user -> new AuthenticatedPrincipal(user.getId(), AuthRole.TEACHER));
        }

        return studentRepository.findById(session.getStudentId())
                .filter(student -> "ACTIVE".equals(student.getStatus()))
                .filter(student -> session.getLoginVersion().equals(student.getLoginVersion()))
                .map(student -> new AuthenticatedPrincipal(student.getId(), AuthRole.STUDENT));
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
