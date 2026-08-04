package com.b201.ecovillage.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.domain.School;
import com.b201.ecovillage.identity.domain.SchoolMembership;
import com.b201.ecovillage.identity.domain.TeacherCredential;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.SchoolMembershipRepository;
import com.b201.ecovillage.identity.repository.SchoolRepository;
import com.b201.ecovillage.identity.repository.TeacherCredentialRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

@Service
public class TeacherRegistrationService {

    private static final Pattern SCHOOL_CODE = Pattern.compile("^[A-Z0-9-]{3,40}$");

    private final SchoolRepository schoolRepository;
    private final UserAccountRepository userAccountRepository;
    private final TeacherCredentialRepository credentialRepository;
    private final SchoolMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final TeacherLoginService teacherLoginService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public TeacherRegistrationService(
        SchoolRepository schoolRepository,
        UserAccountRepository userAccountRepository,
        TeacherCredentialRepository credentialRepository,
        SchoolMembershipRepository membershipRepository,
        PasswordEncoder passwordEncoder,
        TeacherLoginService teacherLoginService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.schoolRepository = schoolRepository;
        this.userAccountRepository = userAccountRepository;
        this.credentialRepository = credentialRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.teacherLoginService = teacherLoginService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public TeacherLoginResult register(
        String schoolCode,
        String displayName,
        String email,
        String password
    ) {
        String normalizedSchoolCode = normalizeSchoolCode(schoolCode);
        String normalizedEmail = TeacherPasswordLoginService.normalizeEmail(email);
        if (credentialRepository.existsByEmail(normalizedEmail)) {
            throw new TeacherRegistrationException(
                HttpStatus.CONFLICT,
                "TEACHER_EMAIL_ALREADY_REGISTERED",
                "이미 가입된 이메일입니다. 로그인해 주세요."
            );
        }
        School school = schoolRepository.findBySchoolCode(normalizedSchoolCode)
            .orElseThrow(() -> new TeacherRegistrationException(
                HttpStatus.NOT_FOUND,
                "SCHOOL_NOT_FOUND",
                "학교 코드를 찾을 수 없습니다."
            ));

        Instant now = clock.instant();
        UUID teacherId = uuidGenerator.generate();
        UserAccount teacher = userAccountRepository.save(
            UserAccount.createTeacher(teacherId, "local:" + teacherId, displayName, now)
        );
        credentialRepository.save(
            TeacherCredential.issue(teacherId, normalizedEmail, passwordEncoder.encode(password), now)
        );
        membershipRepository.save(
            SchoolMembership.assignTeacher(uuidGenerator.generate(), school.getId(), teacherId, now)
        );
        return teacherLoginService.createSession(teacher);
    }

    private static String normalizeSchoolCode(String value) {
        if (value == null) {
            throw invalidSchoolCode();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!SCHOOL_CODE.matcher(normalized).matches()) {
            throw invalidSchoolCode();
        }
        return normalized;
    }

    private static TeacherRegistrationException invalidSchoolCode() {
        return new TeacherRegistrationException(
            HttpStatus.BAD_REQUEST,
            "INVALID_SCHOOL_CODE",
            "학교 코드는 영문 대문자, 숫자, 하이픈 3~40자로 입력해 주세요."
        );
    }
}
