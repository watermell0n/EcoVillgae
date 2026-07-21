package com.b201.bibim.global.config;

import java.util.List;
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
    private static final UUID[] DEMO_PRODUCT_IDS = {
            UUID.fromString("50000000-0000-0000-0000-000000000001"),
            UUID.fromString("50000000-0000-0000-0000-000000000002"),
            UUID.fromString("50000000-0000-0000-0000-000000000003"),
            UUID.fromString("50000000-0000-0000-0000-000000000004"),
            UUID.fromString("50000000-0000-0000-0000-000000000005"),
            UUID.fromString("50000000-0000-0000-0000-000000000006"),
            UUID.fromString("50000000-0000-0000-0000-000000000007"),
            UUID.fromString("50000000-0000-0000-0000-000000000008")
    };

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
        List<DemoStudent> demoStudents = loadDemoStudents();
        seedRoom(teacherId, demoStudents.size());
        seedStudents(demoStudents);
        seedRoomRules();
        seedDayOneSession();
        seedDayOneQuiz();
        seedProducts();
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

    private List<DemoStudent> loadDemoStudents() {
        return jdbcTemplate.query("""
                SELECT participant_number, role_name
                FROM student_role_catalog
                ORDER BY participant_number
                """, (resultSet, rowNumber) -> new DemoStudent(
                resultSet.getInt("participant_number"),
                resultSet.getString("role_name")
        ));
    }

    private void seedRoom(UUID teacherId, int expectedStudentCount) {
        jdbcTemplate.update("""
                INSERT INTO rooms (
                    id, teacher_id, name, expected_student_count, status,
                    current_day, current_stage, mode, version, sync_version
                )
                VALUES (?, ?, '체험용 경제교실', ?, 'DAY1_ACTIVE', 1, 'DAY1_LOBBY', 'GUIDED', 0, 0)
                ON CONFLICT (id) DO UPDATE SET
                    expected_student_count = EXCLUDED.expected_student_count,
                    updated_at = CURRENT_TIMESTAMP
                """,
                DEMO_ROOM_ID,
                teacherId,
                expectedStudentCount
        );
    }

    private void seedStudents(List<DemoStudent> demoStudents) {
        for (DemoStudent student : demoStudents) {
            int number = student.participantNumber();
            UUID studentId = UUID.fromString("30000000-0000-0000-0000-%012d".formatted(number));
            String participationCode = "ECO-%02d".formatted(number);
            jdbcTemplate.update("""
                    INSERT INTO students (
                        id, room_id, participant_number, participation_code, role_name, status
                    )
                    VALUES (?, ?, ?, ?, ?, 'INVITED')
                    ON CONFLICT (participation_code) DO UPDATE SET
                        role_name = COALESCE(students.role_name, EXCLUDED.role_name),
                        updated_at = CURRENT_TIMESTAMP
                    WHERE students.room_id = EXCLUDED.room_id
                      AND students.participant_number = EXCLUDED.participant_number
                    """,
                    studentId,
                    DEMO_ROOM_ID,
                    number,
                    participationCode,
                    student.roleName()
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
                    id, room_id, day_number, title, status, question_count
                )
                VALUES (?, ?, 1, '독서 퀴즈', 'READY', 10)
                ON CONFLICT (room_id, day_number) DO UPDATE SET
                    title = EXCLUDED.title,
                    question_count = EXCLUDED.question_count,
                    updated_at = CURRENT_TIMESTAMP
                """,
                DEMO_DAY_ONE_QUIZ_ID,
                DEMO_ROOM_ID
        );

        DemoQuestion[] questions = {
                new DemoQuestion(
                        "《담을 넘은 아이》의 주인공 이름은 무엇인가요?",
                        "[{\"id\":\"A\",\"text\":\"해님\"},{\"id\":\"B\",\"text\":\"효진\"},{\"id\":\"C\",\"text\":\"푸실\"},{\"id\":\"D\",\"text\":\"귀손\"}]",
                        "C",
                        null
                ),
                new DemoQuestion(
                        "《담을 넘은 아이》를 쓴 사람은 누구인가요?",
                        "[{\"id\":\"A\",\"text\":\"김정민\"},{\"id\":\"B\",\"text\":\"이영환\"},{\"id\":\"C\",\"text\":\"황선미\"},{\"id\":\"D\",\"text\":\"유은실\"}]",
                        "A",
                        null
                ),
                new DemoQuestion(
                        "《담을 넘은 아이》를 출판한 출판사는 어디인가요?",
                        "[{\"id\":\"A\",\"text\":\"비룡소\"},{\"id\":\"B\",\"text\":\"창비\"},{\"id\":\"C\",\"text\":\"문학동네\"},{\"id\":\"D\",\"text\":\"웅진주니어\"}]",
                        "A",
                        null
                ),
                new DemoQuestion(
                        "푸실이의 어머니가 대감댁에 가게 된 이유는 무엇인가요?",
                        "[{\"id\":\"A\",\"text\":\"푸실이에게 글을 가르치기 위해서\"},{\"id\":\"B\",\"text\":\"귀손이의 약값을 갚고 젖어미로 일하기 위해서\"},{\"id\":\"C\",\"text\":\"새로운 집으로 이사하기 위해서\"},{\"id\":\"D\",\"text\":\"효진 아가씨를 만나기 위해서\"}]",
                        "B",
                        null
                ),
                new DemoQuestion(
                        "푸실이가 글을 배우고 싶어 한 가장 큰 이유는 무엇인가요?",
                        "[{\"id\":\"A\",\"text\":\"다른 사람에게 자랑하기 위해\"},{\"id\":\"B\",\"text\":\"자신의 생각을 알고 세상을 더 넓게 보기 위해\"},{\"id\":\"C\",\"text\":\"돈을 많이 벌기 위해\"},{\"id\":\"D\",\"text\":\"집안일을 하지 않기 위해\"}]",
                        "B",
                        null
                ),
                new DemoQuestion(
                        "효진의 아버지는 푸실이가 가진 책을 보고 놀랍니다. 푸실이와 대화를 나눈 뒤, 마지막에 무슨 약속을 꼭 지키라고 했나요?",
                        "[{\"id\":\"A\",\"text\":\"책을 읽고 나서 돌려주라는 약속\"},{\"id\":\"B\",\"text\":\"책의 주인을 꼭 찾아주라는 약속\"},{\"id\":\"C\",\"text\":\"책의 값으로 쌀 두 가마니를 내겠냐는 약속\"},{\"id\":\"D\",\"text\":\"글을 배워서 책을 꼭 읽겠다는 약속\"}]",
                        "D",
                        null
                ),
                new DemoQuestion(
                        "'어머니가 대감마님댁에 간 지도 달포나 지났다.'에서 '달포'의 뜻은 무엇인가요?",
                        "[{\"id\":\"A\",\"text\":\"한 달의 절반\"},{\"id\":\"B\",\"text\":\"딱 한 달\"},{\"id\":\"C\",\"text\":\"한 달이 조금 넘는 기간\"},{\"id\":\"D\",\"text\":\"한 달이 조금 안 되는 기간\"}]",
                        "C",
                        null
                ),
                new DemoQuestion(
                        "책 제목에서 ‘담’이 의미하는 것으로 가장 알맞은 것은 무엇인가요?",
                        "[{\"id\":\"A\",\"text\":\"마을을 지키는 실제 성벽만을 뜻한다.\"},{\"id\":\"B\",\"text\":\"사람들이 함께 쉬는 장소를 뜻한다.\"},{\"id\":\"C\",\"text\":\"푸실이를 가로막는 차별과 사회의 규칙을 뜻한다.\"},{\"id\":\"D\",\"text\":\"푸실이의 가족이 사는 집을 뜻한다.\"}]",
                        "C",
                        null
                ),
                new DemoQuestion(
                        "152쪽, 이 책의 마지막 문장은 무엇인지 고르시오.",
                        "[{\"id\":\"A\",\"text\":\"어찌 살 것입니까.\"},{\"id\":\"B\",\"text\":\"저는 담을 넘겠습니다.\"},{\"id\":\"C\",\"text\":\"더 힘차게 앞으로 나아가겠습니다.\"}]",
                        "A",
                        null
                ),
                new DemoQuestion(
                        "《담을 넘은 아이》가 우리에게 전하는 생각으로 가장 알맞은 것은 무엇인가요?",
                        "[{\"id\":\"A\",\"text\":\"옛날의 규칙은 언제나 지켜야 한다.\"},{\"id\":\"B\",\"text\":\"태어난 환경에 따라 사람의 가능성이 정해진다.\"},{\"id\":\"C\",\"text\":\"어려움이 생기면 자신의 꿈을 포기해야 한다.\"},{\"id\":\"D\",\"text\":\"차별과 편견을 넘어 자신의 삶을 스스로 선택할 수 있다.\"}]",
                        "D",
                        null
                )
        };

        for (int index = 0; index < questions.length; index += 1) {
            int orderNo = index + 1;
            DemoQuestion question = questions[index];
            UUID questionId = UUID.fromString(
                    "41000000-0000-0000-0000-%012d".formatted(orderNo)
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

    private void seedProducts() {
        DemoProduct[] products = {
                new DemoProduct(
                        "말랑카우 빠삐코",
                        "달콤하고 시원하게 즐기는 간식 세트예요.",
                        45,
                        38,
                        "/api/v1/product-images/b6fbfc5e-5580-42f3-82da-45caf5bc2b27.png"
                ),
                new DemoProduct(
                        "쿠크다스케이크",
                        "부드럽고 달콤한 케이크 간식이에요.",
                        80,
                        10,
                        "/api/v1/product-images/61466d81-4880-4f87-9113-4d4cf27b7547.png"
                ),
                new DemoProduct(
                        "마이쭈 딸기",
                        "쫀득하게 즐기는 딸기맛 간식이에요.",
                        40,
                        18,
                        "/api/v1/product-images/4e97e1d8-008f-4c63-95e9-1b8553349120.png"
                ),
                new DemoProduct(
                        "포도 알맹이",
                        "탱글탱글한 포도맛 젤리 간식이에요.",
                        100,
                        6,
                        "/api/v1/product-images/74ff9a68-1a71-4cfe-b1f4-71c64f54aa17.png"
                ),
                new DemoProduct(
                        "카피바라 지우개",
                        "귀여운 카피바라 모양 지우개예요.",
                        145,
                        3,
                        "/api/v1/product-images/73aa1e91-9296-4035-b5c0-4850a4c45c8a.png"
                ),
                new DemoProduct(
                        "과일말랑이",
                        "말랑한 과일 모양 촉감 장난감이에요.",
                        180,
                        2,
                        "/api/v1/product-images/3c617161-dea7-4530-ae31-76d15dd3b7b9.png"
                ),
                new DemoProduct(
                        "왁뿌볼",
                        "재미있게 가지고 놀 수 있는 말랑한 공이에요.",
                        250,
                        1,
                        "/api/v1/product-images/03a642cb-f92e-45ee-b0ac-a02cc303327d.png"
                ),
                new DemoProduct(
                        "배움공책 면제권",
                        "배움공책 활동을 한 번 면제받을 수 있는 이용권이에요.",
                        110,
                        15,
                        "/api/v1/product-images/2a6df38e-e054-41c7-9716-15e0e321ff2d.png"
                )
        };

        for (int index = 0; index < products.length; index += 1) {
            DemoProduct product = products[index];
            jdbcTemplate.update("""
                    INSERT INTO products (
                        id, room_id, name, description, original_price, current_price,
                        initial_stock, current_stock, image_url, status, display_order
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ON_SALE', ?)
                    ON CONFLICT DO NOTHING
                    """,
                    DEMO_PRODUCT_IDS[index],
                    DEMO_ROOM_ID,
                    product.name(),
                    product.description(),
                    product.price(),
                    product.price(),
                    product.stock(),
                    product.stock(),
                    product.imageUrl(),
                    index + 1
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

    private record DemoStudent(int participantNumber, String roleName) {
    }

    private record DemoProduct(String name, String description, long price, int stock, String imageUrl) {
    }
}
