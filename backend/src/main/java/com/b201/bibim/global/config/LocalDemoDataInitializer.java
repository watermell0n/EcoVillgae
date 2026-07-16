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
    private static final UUID DEFAULT_ADMIN_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID DEMO_ROOM_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID DEMO_RULE_ID = UUID.fromString("21000000-0000-0000-0000-000000000001");
    private static final UUID DEMO_SESSION_ID = UUID.fromString("22000000-0000-0000-0000-000000000001");
    private static final UUID DEMO_DAY_ONE_QUIZ_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000001"
    );

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String teacherLoginId;
    private final String teacherPassword;
    private final String teacherDisplayName;
    private final String adminLoginId;
    private final String adminPassword;
    private final String adminDisplayName;

    public LocalDemoDataInitializer(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${app.demo.teacher-login-id}") String teacherLoginId,
            @Value("${app.demo.teacher-password}") String teacherPassword,
            @Value("${app.demo.teacher-display-name}") String teacherDisplayName,
            @Value("${app.demo.admin-login-id}") String adminLoginId,
            @Value("${app.demo.admin-password}") String adminPassword,
            @Value("${app.demo.admin-display-name}") String adminDisplayName
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.teacherLoginId = teacherLoginId;
        this.teacherPassword = teacherPassword;
        this.teacherDisplayName = teacherDisplayName;
        this.adminLoginId = adminLoginId;
        this.adminPassword = adminPassword;
        this.adminDisplayName = adminDisplayName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedTeacher();
        seedAdmin();
        UUID teacherId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login_id = ?",
                UUID.class,
                teacherLoginId
        );
        seedRoom(teacherId);
        seedStudents();
        seedRoomRules();
        seedDayOneSession();
        seedDayOneQuiz();
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

    private void seedAdmin() {
        jdbcTemplate.update("""
                INSERT INTO users (id, role, login_id, password_hash, display_name, status)
                VALUES (?, 'ADMIN', ?, ?, ?, 'ACTIVE')
                ON CONFLICT (login_id) DO UPDATE SET
                    password_hash = EXCLUDED.password_hash,
                    display_name = EXCLUDED.display_name,
                    status = 'ACTIVE',
                    updated_at = CURRENT_TIMESTAMP
                """,
                DEFAULT_ADMIN_ID,
                adminLoginId,
                passwordEncoder.encode(adminPassword),
                adminDisplayName
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

    private void seedDayOneQuiz() {
        jdbcTemplate.update("""
                INSERT INTO quizzes (
                    id, room_id, day_number, title, status, question_count, opened_at
                )
                VALUES (?, ?, 1, '1일차 월급 퀴즈', 'OPEN', 6, CURRENT_TIMESTAMP)
                ON CONFLICT (room_id, day_number) DO UPDATE SET
                    title = EXCLUDED.title,
                    status = 'OPEN',
                    question_count = EXCLUDED.question_count,
                    opened_at = COALESCE(quizzes.opened_at, CURRENT_TIMESTAMP),
                    updated_at = CURRENT_TIMESTAMP
                """,
                DEMO_DAY_ONE_QUIZ_ID,
                DEMO_ROOM_ID
        );

        DemoQuestion[] questions = {
                new DemoQuestion(
                        "내가 맡은 역할의 일을 하고 받는 돈을 무엇이라고 할까요?",
                        "[{\"id\":\"A\",\"text\":\"역할 소득\"},{\"id\":\"B\",\"text\":\"예금 이자\"},{\"id\":\"C\",\"text\":\"투자 손실\"},{\"id\":\"D\",\"text\":\"상품 가격\"}]",
                        "A",
                        "일한 대가로 받는 돈을 소득이라고 합니다."
                ),
                new DemoQuestion(
                        "역할 소득 100비빔과 퀴즈 참여수당 10비빔을 받으면 총소득은 얼마일까요?",
                        "[{\"id\":\"A\",\"text\":\"90비빔\"},{\"id\":\"B\",\"text\":\"100비빔\"},{\"id\":\"C\",\"text\":\"110비빔\"},{\"id\":\"D\",\"text\":\"120비빔\"}]",
                        "C",
                        "100비빔과 10비빔을 더하면 110비빔입니다."
                ),
                new DemoQuestion(
                        "소득의 일부를 세금으로 내는 이유로 가장 알맞은 것은 무엇일까요?",
                        "[{\"id\":\"A\",\"text\":\"돈을 모두 없애기 위해서\"},{\"id\":\"B\",\"text\":\"함께 필요한 곳에 사용하기 위해서\"},{\"id\":\"C\",\"text\":\"상품 재고를 줄이기 위해서\"},{\"id\":\"D\",\"text\":\"투자 위험을 높이기 위해서\"}]",
                        "B",
                        "세금은 공동체에 필요한 일과 서비스를 위해 사용됩니다."
                ),
                new DemoQuestion(
                        "돈을 비교적 안전하게 맡기고 약속된 이자를 받는 활동은 무엇일까요?",
                        "[{\"id\":\"A\",\"text\":\"소비\"},{\"id\":\"B\",\"text\":\"예금\"},{\"id\":\"C\",\"text\":\"투표\"},{\"id\":\"D\",\"text\":\"세금\"}]",
                        "B",
                        "예금은 돈을 일정 기간 맡기고 이자를 받는 금융 활동입니다."
                ),
                new DemoQuestion(
                        "일반적으로 더 큰 수익을 기대하는 투자일수록 무엇도 커질 수 있을까요?",
                        "[{\"id\":\"A\",\"text\":\"손실 위험\"},{\"id\":\"B\",\"text\":\"참여 코드\"},{\"id\":\"C\",\"text\":\"학급 인원\"},{\"id\":\"D\",\"text\":\"예금 기간\"}]",
                        "A",
                        "기대 수익이 큰 투자에는 보통 더 큰 손실 위험이 따릅니다."
                ),
                new DemoQuestion(
                        "투자금을 여러 곳에 나누어 투자하는 방법을 무엇이라고 할까요?",
                        "[{\"id\":\"A\",\"text\":\"충동구매\"},{\"id\":\"B\",\"text\":\"소득세\"},{\"id\":\"C\",\"text\":\"분산투자\"},{\"id\":\"D\",\"text\":\"중도 인출\"}]",
                        "C",
                        "분산투자는 투자 대상을 나누어 위험을 줄이는 방법입니다."
                )
        };

        for (int index = 0; index < questions.length; index += 1) {
            int orderNo = index + 1;
            DemoQuestion question = questions[index];
            UUID questionId = UUID.fromString(
                    "41000000-0000-0000-0000-00000000000" + orderNo
            );
            jdbcTemplate.update("""
                    INSERT INTO quiz_questions (
                        id, quiz_id, order_no, question_type, prompt,
                        options, correct_answer, explanation
                    )
                    VALUES (?, ?, ?, 'MULTIPLE_CHOICE', ?, ?::jsonb, ?, ?)
                    ON CONFLICT (quiz_id, order_no) DO UPDATE SET
                        prompt = EXCLUDED.prompt,
                        options = EXCLUDED.options,
                        correct_answer = EXCLUDED.correct_answer,
                        explanation = EXCLUDED.explanation
                    """,
                    questionId,
                    DEMO_DAY_ONE_QUIZ_ID,
                    orderNo,
                    question.prompt(),
                    question.options(),
                    question.correctAnswer(),
                    question.explanation()
            );
        }
    }

    private record DemoQuestion(
            String prompt,
            String options,
            String correctAnswer,
            String explanation
    ) {
    }
}
