package com.b201.ecovillage.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
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
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;

@Service
public class StudentLoginService {

    private static final Pattern CLASSROOM_CODE =
        Pattern.compile("^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}$");

    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRepository classroomStudentRepository;
    private final StudentCredentialRepository studentCredentialRepository;
    private final DeviceLinkRepository deviceLinkRepository;
    private final StudentSessionRepository studentSessionRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
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
        LedgerAccountRepository ledgerAccountRepository,
        @Qualifier("studentPasswordEncoder") PasswordEncoder passwordEncoder,
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
        this.ledgerAccountRepository = ledgerAccountRepository;
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

        String nickname = normalizeNickname(command.nickname());
        validatePassword(command.password());
        StudentAccess studentAccess = findExistingAccess(classroom, command, nickname)
            .orElseGet(() -> registerOrClaim(classroom, command, nickname, now));
        ClassroomStudent student = studentAccess.student();
        StudentCredential credential = studentAccess.credential();

        if (!studentAccess.created()
            && !passwordEncoder.matches(command.password(), credential.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        ensureStudentWallet(classroom.getId(), student.getId(), now);

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
            classroom.getCurrencyName(),
            ledgerAccountRepository
                .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                    classroom.getId(),
                    student.getId(),
                    LedgerAccountType.STUDENT_WALLET,
                    LedgerAccountStatus.ACTIVE
                )
                .map(account -> account.getCurrentBalance())
                .orElse(0L),
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

    private Optional<StudentAccess> findExistingAccess(
        Classroom classroom,
        StudentLoginCommand command,
        String nickname
    ) {
        Optional<ClassroomStudent> student = command.attendanceNumber() == null
            ? classroomStudentRepository
                .findByClassroomIdAndNicknameIgnoreCaseAndRetiredAtIsNull(
                    classroom.getId(),
                    nickname
                )
            : classroomStudentRepository
                .findForLogin(classroom.getId(), command.attendanceNumber())
                .filter(candidate -> nicknameMatches(candidate, nickname));
        return student
            .filter(candidate -> candidate.getStatus() == ClassroomStudentStatus.ACTIVE)
            .flatMap(candidate -> studentCredentialRepository
                .findByClassroomIdAndClassroomStudentId(classroom.getId(), candidate.getId())
                .map(credential -> new StudentAccess(candidate, credential, false)));
    }

    private StudentAccess registerOrClaim(
        Classroom classroom,
        StudentLoginCommand command,
        String nickname,
        Instant now
    ) {
        Classroom lockedClassroom = classroomRepository.findForUpdateById(classroom.getId())
            .orElseThrow(InvalidCredentialsException::new);
        if (!lockedClassroom.allowsStudentLogin(now)) {
            throw new ClassroomNotActiveException();
        }

        if (command.attendanceNumber() != null) {
            classroomStudentRepository
                .findForLogin(lockedClassroom.getId(), command.attendanceNumber())
                .filter(candidate -> candidate.getStatus() == ClassroomStudentStatus.ACTIVE)
                .ifPresent(candidate -> {
                    if (!nicknameMatches(candidate, nickname)) {
                        throw new InvalidCredentialsException();
                    }
                });
        }

        Optional<ClassroomStudent> existingByNickname = classroomStudentRepository
            .findByClassroomIdAndNicknameIgnoreCaseAndRetiredAtIsNull(
                lockedClassroom.getId(),
                nickname
            );
        if (existingByNickname.isPresent()) {
            ClassroomStudent student = existingByNickname.get();
            if (student.getStatus() != ClassroomStudentStatus.ACTIVE) {
                throw new InvalidCredentialsException();
            }
            Optional<StudentCredential> existingCredential = studentCredentialRepository
                .findByClassroomIdAndClassroomStudentId(lockedClassroom.getId(), student.getId());
            if (existingCredential.isPresent()) {
                if (command.attendanceNumber() != null
                    && command.attendanceNumber() != student.getRosterNumber()) {
                    throw new InvalidCredentialsException();
                }
                return new StudentAccess(student, existingCredential.get(), false);
            }
            StudentCredential credential = studentCredentialRepository.save(
                StudentCredential.issue(
                    student.getId(),
                    lockedClassroom.getId(),
                    passwordEncoder.encode(command.password()),
                    now
                )
            );
            return new StudentAccess(student, credential, true);
        }

        Optional<StudentAccess> placeholder = claimUnassignedPlaceholder(
            lockedClassroom,
            nickname,
            command.password(),
            now
        );
        if (placeholder.isPresent()) {
            return placeholder.get();
        }

        List<ClassroomStudent> activeStudents = classroomStudentRepository
            .findAllByClassroomIdAndStatusOrderByRosterNumber(
                lockedClassroom.getId(),
                ClassroomStudentStatus.ACTIVE
            );
        if (activeStudents.size() >= lockedClassroom.getPlannedStudentCount()) {
            throw new StudentRegistrationException(
                HttpStatus.CONFLICT,
                "CLASSROOM_FULL",
                "학급 정원이 모두 찼습니다. 선생님에게 문의해 주세요."
            );
        }
        int rosterNumber = resolveRosterNumber(
            command.attendanceNumber(),
            activeStudents,
            lockedClassroom.getPlannedStudentCount()
        );
        UUID studentId = uuidGenerator.generate();
        ClassroomStudent student = classroomStudentRepository.save(
            ClassroomStudent.enroll(
                studentId,
                lockedClassroom.getId(),
                rosterNumber,
                nickname,
                now
            )
        );
        StudentCredential credential = studentCredentialRepository.save(
            StudentCredential.issue(
                studentId,
                lockedClassroom.getId(),
                passwordEncoder.encode(command.password()),
                now
            )
        );
        classroomStudentRepository.flush();
        studentCredentialRepository.flush();
        return new StudentAccess(student, credential, true);
    }

    private Optional<StudentAccess> claimUnassignedPlaceholder(
        Classroom classroom,
        String nickname,
        String password,
        Instant now
    ) {
        return classroomStudentRepository
            .findAllByClassroomIdAndStatusOrderByRosterNumber(
                classroom.getId(),
                ClassroomStudentStatus.ACTIVE
            )
            .stream()
            .filter(candidate -> candidate.getNickname().equals(initialStudentNickname(candidate.getRosterNumber())))
            .filter(candidate -> studentCredentialRepository
                .findByClassroomIdAndClassroomStudentId(classroom.getId(), candidate.getId())
                .isEmpty()
            )
            .findFirst()
            .map(candidate -> {
                candidate.changeNickname(nickname, now);
                StudentCredential credential = studentCredentialRepository.save(
                    StudentCredential.issue(
                        candidate.getId(),
                        classroom.getId(),
                        passwordEncoder.encode(password),
                        now
                    )
                );
                return new StudentAccess(candidate, credential, true);
            });
    }

    private static String initialStudentNickname(int rosterNumber) {
        return "학생 " + rosterNumber;
    }

    private static int resolveRosterNumber(
        Integer requested,
        List<ClassroomStudent> activeStudents,
        int capacity
    ) {
        Set<Integer> used = activeStudents.stream()
            .map(ClassroomStudent::getRosterNumber)
            .collect(Collectors.toSet());
        if (requested != null) {
            if (requested > capacity || used.contains(requested)) {
                throw new StudentRegistrationException(
                    HttpStatus.CONFLICT,
                    "ATTENDANCE_NUMBER_UNAVAILABLE",
                    "해당 출석번호는 사용할 수 없습니다."
                );
            }
            return requested;
        }
        for (int candidate = 1; candidate <= capacity; candidate++) {
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
        throw new StudentRegistrationException(
            HttpStatus.CONFLICT,
            "CLASSROOM_FULL",
            "학급 정원이 모두 찼습니다."
        );
    }

    private void ensureStudentWallet(UUID classroomId, UUID studentId, Instant now) {
        if (ledgerAccountRepository
            .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                classroomId,
                studentId,
                LedgerAccountType.STUDENT_WALLET,
                LedgerAccountStatus.ACTIVE
            )
            .isEmpty()) {
            ledgerAccountRepository.save(
                LedgerAccount.studentWallet(uuidGenerator.generate(), classroomId, studentId, now)
            );
        }
    }

    private static String normalizeNickname(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidLoginRequestException("nickname must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > 40) {
            throw new InvalidLoginRequestException("nickname must not exceed 40 characters");
        }
        return normalized;
    }

    private static void validatePassword(String value) {
        if (value == null || value.length() < 4 || value.length() > 72) {
            throw new InvalidLoginRequestException("password must contain 4 to 72 characters");
        }
    }

    private static String rateLimitKey(StudentLoginCommand command, String classroomCode) {
        String remoteAddress = command.remoteAddress() == null
            ? "unknown"
            : command.remoteAddress();
        return remoteAddress + ":" + classroomCode + ":" + command.deviceId();
    }

    private record StudentAccess(
        ClassroomStudent student,
        StudentCredential credential,
        boolean created
    ) {
    }
}
