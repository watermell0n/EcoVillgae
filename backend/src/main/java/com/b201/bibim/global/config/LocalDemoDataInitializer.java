package com.b201.bibim.global.config;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
public class LocalDemoDataInitializer implements ApplicationRunner {

    private static final UUID DEFAULT_TEACHER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DEMO_ROOM_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID DEMO_RULE_ID = UUID.fromString("21000000-0000-0000-0000-000000000001");
    private static final UUID DEMO_SESSION_ID = UUID.fromString("22000000-0000-0000-0000-000000000001");

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String teacherLoginId;
    private final String teacherPassword;
    private final String teacherDisplayName;

    public LocalDemoDataInitializer(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${app.demo.teacher-login-id}") String teacherLoginId,
            @Value("${app.demo.teacher-password}") String teacherPassword,
            @Value("${app.demo.teacher-display-name}") String teacherDisplayName
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.teacherLoginId = teacherLoginId;
        this.teacherPassword = teacherPassword;
        this.teacherDisplayName = teacherDisplayName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedTeacher();
        UUID teacherId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login_id = ?",
                UUID.class,
                teacherLoginId
        );
        seedRoom(teacherId);
        seedStudents();
        seedRoomRules();
        seedDayOneSession();
    }

    private void seedTeacher() {
        jdbcTemplate.update("""
                INSERT INTO users (id, role, login_id, password_hash, display_name, status)
                VALUES (?, 'TEACHER', ?, ?, ?, 'ACTIVE')
                ON CONFLICT (login_id) DO UPDATE SET
                    password_hash = EXCLUDED.password_hash,
                    display_name = EXCLUDED.display_name,
                    status = 'ACTIVE',
                    updated_at = CURRENT_TIMESTAMP
                """,
                DEFAULT_TEACHER_ID,
                teacherLoginId,
                passwordEncoder.encode(teacherPassword),
                teacherDisplayName
        );
    }

    private void seedRoom(UUID teacherId) {
        jdbcTemplate.update("""
                INSERT INTO rooms (
                    id, teacher_id, name, expected_student_count, status,
                    current_day, current_stage, mode, version, sync_version
                )
                VALUES (?, ?, '6학년 2반 경제교실', 3, 'DAY1_ACTIVE', 1, 'DAY1_LOBBY', 'GUIDED', 0, 0)
                ON CONFLICT (id) DO NOTHING
                """,
                DEMO_ROOM_ID,
                teacherId
        );
    }

    private void seedStudents() {
        for (int number = 1; number <= 3; number += 1) {
            UUID studentId = UUID.fromString("30000000-0000-0000-0000-00000000000" + number);
            String participationCode = "BIBIM-0" + number;
            jdbcTemplate.update("""
                    INSERT INTO students (
                        id, room_id, participant_number, participation_code, status
                    )
                    VALUES (?, ?, ?, ?, 'INVITED')
                    ON CONFLICT (participation_code) DO NOTHING
                    """,
                    studentId,
                    DEMO_ROOM_ID,
                    number,
                    participationCode
            );
        }
    }

    private void seedRoomRules() {
        jdbcTemplate.update("""
                INSERT INTO room_rules (id, room_id)
                VALUES (?, ?)
                ON CONFLICT (room_id) DO NOTHING
                """,
                DEMO_RULE_ID,
                DEMO_ROOM_ID
        );
    }

    private void seedDayOneSession() {
        jdbcTemplate.update("""
                INSERT INTO sessions (id, room_id, day_number, status, current_stage, started_at)
                VALUES (?, ?, 1, 'ACTIVE', 'DAY1_LOBBY', CURRENT_TIMESTAMP)
                ON CONFLICT (room_id, day_number) DO NOTHING
                """,
                DEMO_SESSION_ID,
                DEMO_ROOM_ID
        );
    }
}
