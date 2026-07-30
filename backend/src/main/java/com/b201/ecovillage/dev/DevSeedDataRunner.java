package com.b201.ecovillage.dev;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.identity.domain.StudentCredential;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.StudentCredentialRepository;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;

@Component
@ConditionalOnProperty(prefix = "ecovillage.dev-seed", name = "enabled", havingValue = "true")
public class DevSeedDataRunner implements ApplicationRunner {

    public static final String CLASSROOM_CODE = "ABCD2345";
    public static final int ATTENDANCE_NUMBER = 7;
    public static final String NICKNAME = "\uACBD\uC81C\uC655";
    public static final String PASSWORD = "economy123!";

    private static final UUID SCHOOL_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID CLASSROOM_ID = UUID.fromString("00000000-0000-4000-8000-000000000101");
    private static final UUID STUDENT_ID = UUID.fromString("00000000-0000-4000-8000-000000000201");
    private static final UUID WALLET_ID = UUID.fromString("00000000-0000-4000-8000-000000000301");

    private final JdbcTemplate jdbcTemplate;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRepository classroomStudentRepository;
    private final StudentCredentialRepository studentCredentialRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DevSeedDataRunner(
        JdbcTemplate jdbcTemplate,
        ClassroomRepository classroomRepository,
        ClassroomStudentRepository classroomStudentRepository,
        StudentCredentialRepository studentCredentialRepository,
        LedgerAccountRepository ledgerAccountRepository,
        PasswordEncoder passwordEncoder,
        Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.classroomRepository = classroomRepository;
        this.classroomStudentRepository = classroomStudentRepository;
        this.studentCredentialRepository = studentCredentialRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var now = clock.instant();
        seedSchool();

        Classroom classroom = classroomRepository
            .findByClassroomCode(CLASSROOM_CODE)
            .orElseGet(() -> classroomRepository.save(Classroom.create(
                CLASSROOM_ID,
                SCHOOL_ID,
                CLASSROOM_CODE,
                "\uBE44\uBE54",
                4,
                CourseSize.ONE_WEEK,
                1,
                LocalDate.now(clock),
                LocalDate.now(clock).plusDays(7),
                "Asia/Seoul",
                now
            )));

        ClassroomStudent student = classroomStudentRepository
            .findByClassroomIdAndRosterNumberAndRetiredAtIsNull(
                classroom.getId(),
                ATTENDANCE_NUMBER
            )
            .map(existing -> {
                existing.changeNickname(NICKNAME, now);
                return existing;
            })
            .orElseGet(() -> classroomStudentRepository.save(ClassroomStudent.enroll(
                STUDENT_ID,
                classroom.getId(),
                ATTENDANCE_NUMBER,
                NICKNAME,
                now
            )));

        StudentCredential credential = studentCredentialRepository
            .findByClassroomIdAndClassroomStudentId(classroom.getId(), student.getId())
            .orElseGet(() -> StudentCredential.issue(
                student.getId(),
                classroom.getId(),
                passwordEncoder.encode(PASSWORD),
                now
            ));
        credential.replacePasswordHash(passwordEncoder.encode(PASSWORD), now);
        studentCredentialRepository.save(credential);

        ledgerAccountRepository
            .findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(
                classroom.getId(),
                student.getId(),
                LedgerAccountType.STUDENT_WALLET,
                LedgerAccountStatus.ACTIVE
            )
            .orElseGet(() -> ledgerAccountRepository.save(LedgerAccount.studentWallet(
                WALLET_ID,
                classroom.getId(),
                student.getId(),
                now
            )));
    }

    private void seedSchool() {
        jdbcTemplate.update(
            """
            INSERT INTO schools (id, school_code, name, status)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (school_code) DO NOTHING
            """,
            SCHOOL_ID,
            "DEV-SCHOOL",
            "\uC774\uCF54\uBE4C\uB9AC\uC9C0 \uAC1C\uBC1C\uD559\uAD50",
            "ACTIVE"
        );
    }
}
