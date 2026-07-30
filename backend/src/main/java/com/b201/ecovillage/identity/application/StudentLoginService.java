package com.b201.ecovillage.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.domain.DeviceLink;
import com.b201.ecovillage.identity.domain.StudentCredential;
import com.b201.ecovillage.identity.domain.StudentSession;
import com.b201.ecovillage.identity.domain.StudentSessionStatus;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.DeviceLinkRepository;
import com.b201.ecovillage.identity.repository.StudentCredentialRepository;
import com.b201.ecovillage.identity.repository.StudentSessionRepository;
import com.b201.ecovillage.identity.security.IdentitySecretHasher;
import com.b201.ecovillage.identity.security.IssuedStudentTokens;
import com.b201.ecovillage.identity.security.StudentTokenIssuer;

@Service
public class StudentLoginService {

    private static final Pattern CLASSROOM_CODE =
        Pattern.compile("^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}$");

    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRepository classroomStudentRepository;
    private final StudentCredentialRepository studentCredentialRepository;
    private final DeviceLinkRepository deviceLinkRepository;
    private final StudentSessionRepository studentSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final StudentTokenIssuer tokenIssuer;
    private final IdentitySecretHasher secretHasher;
    private final StudentLoginRateLimiter rateLimiter;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public StudentLoginService(
        ClassroomRepository classroomRepository,
        ClassroomStudentRepository classroomStudentRepository,
        StudentCredentialRepository studentCredentialRepository,
        DeviceLinkRepository deviceLinkRepository,
        StudentSessionRepository studentSessionRepository,
        PasswordEncoder passwordEncoder,
        StudentTokenIssuer tokenIssuer,
        IdentitySecretHasher secretHasher,
        StudentLoginRateLimiter rateLimiter,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.classroomRepository = classroomRepository;
        this.classroomStudentRepository = classroomStudentRepository;
        this.studentCredentialRepository = studentCredentialRepository;
        this.deviceLinkRepository = deviceLinkRepository;
        this.studentSessionRepository = studentSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.secretHasher = secretHasher;
        this.rateLimiter = rateLimiter;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public StudentLoginResult login(StudentLoginCommand command) {
        Instant now = clock.instant();
        String classroomCode = normalizeClassroomCode(command.classroomCode());
        rateLimiter.check(rateLimitKey(command, classroomCode), now);

        Classroom classroom = classroomRepository
            .findByClassroomCode(classroomCode)
            .orElseThrow(InvalidCredentialsException::new);
        if (!classroom.allowsStudentLogin(now)) {
            throw new ClassroomNotActiveException();
        }

        ClassroomStudent student = classroomStudentRepository
            .findForLogin(classroom.getId(), command.attendanceNumber())
            .filter(candidate -> candidate.getStatus() == ClassroomStudentStatus.ACTIVE)
            .filter(candidate -> nicknameMatches(candidate, command.nickname()))
            .orElseThrow(InvalidCredentialsException::new);
        StudentCredential credential = studentCredentialRepository
            .findByClassroomIdAndClassroomStudentId(classroom.getId(), student.getId())
            .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(command.password(), credential.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String deviceFingerprintHash = secretHasher.hashDeviceId(command.deviceId());
        DeviceLink deviceLink = deviceLinkRepository
            .findByClassroomStudentIdAndDeviceFingerprintHash(
                student.getId(),
                deviceFingerprintHash
            )
            .map(existing -> {
                existing.activate(now);
                return existing;
            })
            .orElseGet(() -> DeviceLink.link(
                uuidGenerator.generate(),
                classroom.getId(),
                student.getId(),
                deviceFingerprintHash,
                now
            ));
        deviceLinkRepository.save(deviceLink);

        List<StudentSession> activeSessions = studentSessionRepository
            .findAllByClassroomStudentIdAndStatus(
                student.getId(),
                StudentSessionStatus.ACTIVE
            );
        activeSessions.forEach(session -> session.revoke(now));
        if (!activeSessions.isEmpty()) {
            studentSessionRepository.flush();
        }

        UUID sessionId = uuidGenerator.generate();
        IssuedStudentTokens tokens = tokenIssuer.issue(
            sessionId,
            classroom.getId(),
            student.getId(),
            credential.getCredentialVersion(),
            now
        );
        studentSessionRepository.saveAndFlush(
            StudentSession.issue(
                sessionId,
                classroom.getId(),
                student.getId(),
                deviceLink.getId(),
                tokens.refreshTokenHash(),
                tokens.refreshTokenExpiresAt(),
                now
            )
        );

        return new StudentLoginResult(
            tokens.accessToken(),
            tokens.refreshToken(),
            tokens.accessTokenExpiresAt(),
            classroom.getId(),
            student.getId()
        );
    }

    private static String normalizeClassroomCode(String value) {
        if (value == null) {
            throw new InvalidLoginRequestException("classroomCode must not be null");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!CLASSROOM_CODE.matcher(normalized).matches()) {
            throw new InvalidLoginRequestException("classroomCode format is invalid");
        }
        return normalized;
    }

    private static boolean nicknameMatches(ClassroomStudent student, String nickname) {
        return nickname != null
            && student.getNickname().equalsIgnoreCase(nickname.trim());
    }

    private static String rateLimitKey(StudentLoginCommand command, String classroomCode) {
        String remoteAddress = command.remoteAddress() == null
            ? "unknown"
            : command.remoteAddress();
        return remoteAddress + ":" + classroomCode + ":" + command.deviceId();
    }
}
